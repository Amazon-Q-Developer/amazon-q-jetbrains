// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.File

class WebviewFunctionCallTest {
    private val mapper = jacksonObjectMapper()

    // A valid JS string-literal argument is ONE JSON string token and nothing else, so a strict parse must
    // consume all of it. The trailing tokens that a name which is not self-contained would introduce make this
    // parse fail, which is what distinguishes inert data from executable code.
    private val strict = jacksonObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    /**
     * Decode an argument the way the webview does: the argument is itself a JSON string whose value is the
     * JSON document the handler then `JSON.parse()`s, so we unwrap it twice. The strict parser throws if the
     * argument is not exactly one self-contained string literal.
     */
    private inline fun <reified T> decodeArg(arg: String): T {
        val innerJson: String = strict.readValue(arg)
        return strict.readValue(innerJson)
    }

    /**
     * Oracle modelling how a JS engine reads the argument: starting at the opening quote `arg[0]`, find the
     * first UNESCAPED matching quote. The literal is self-contained — nothing after it can execute — only if
     * that quote is the final character. An unescaped matching quote inside the name is the case where it is not.
     */
    private fun isSelfContainedStringLiteral(arg: String): Boolean {
        if (arg.length < 2) return false
        val quote = arg[0]
        if (quote != '"' && quote != '\'') return false
        var i = 1
        while (i < arg.length) {
            when (arg[i]) {
                '\\' -> {
                    i += 2 // skip the escaped character
                    continue
                }
                quote -> return i == arg.length - 1
            }
            i++
        }
        return false
    }

    @Test
    fun `benign file names round-trip unchanged`() {
        val files = listOf("/home/me/a.png", "/home/me/b image.png")
        val arg = toJsStringLiteralArg(mapper, files)
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).isEqualTo(files)
    }

    @Test
    fun `a single quote in a file name stays inside the literal`() {
        val rawPath = "/tmp/x');fn(value);//.png"
        val arg = toJsStringLiteralArg(mapper, listOf(rawPath))
        // The name is inert data inside one self-contained literal ...
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        // ... and the exact name still reaches the webview, so nothing is dropped or corrupted.
        assertThat(decodeArg<List<String>>(arg)).containsExactly(rawPath)
    }

    @Test
    fun `the previous single-quote-wrapped construction was not self-contained (control)`() {
        val rawPath = "/tmp/x');fn(1);//.png"
        // Reconstructs the previous construction: wrapping the JSON text in single quotes.
        val naiveLiteral = "'" + mapper.writeValueAsString(listOf(rawPath)) + "'"
        // Proves the oracle actually detects the defect this change removes.
        assertThat(isSelfContainedStringLiteral(naiveLiteral)).isFalse()
    }

    @Test
    fun `double quotes and backslashes cannot terminate the literal`() {
        val rawPath = """/tmp/a"</tag><elem a=x b=fn(1)>\end.png"""
        val arg = toJsStringLiteralArg(mapper, listOf(rawPath))
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).containsExactly(rawPath)
    }

    @Test
    fun `JS line terminators U+2028 and U+2029 are escaped`() {
        val rawPath = "/tmp/a\u2028\u2029b.png"
        val arg = toJsStringLiteralArg(mapper, listOf(rawPath))
        assertThat(arg).doesNotContain("\u2028").doesNotContain("\u2029")
        assertThat(arg).contains("\\u2028").contains("\\u2029")
        assertThat(decodeArg<List<String>>(arg)).containsExactly(rawPath)
    }

    @Test
    fun `error messages are encoded safely`() {
        val errors = listOf("bad'name: File must be an image", """other"name""")
        val arg = toJsStringLiteralArg(mapper, errors)
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).isEqualTo(errors)
    }

    @Test
    fun `a dropped File with a quote in its name is encoded from its path`() {
        // Production passes List<File>; Jackson serializes a File to its absolute path.
        val file = File("/tmp/x'y\";name.png")
        val arg = toJsStringLiteralArg(mapper, listOf(file))
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).containsExactly(file.absolutePath)
    }

    @Test
    fun `windows backslash paths survive intact`() {
        // The previous single-quoted construction corrupted these; the fix must round-trip them exactly.
        val windows = """C:\Users\me\a'b.png"""
        val arg = toJsStringLiteralArg(mapper, listOf(windows))
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).containsExactly(windows)
    }

    @Test
    fun `newline and NUL control characters are escaped`() {
        val rawPath = "/tmp/a\n\r\u0000b.png"
        val arg = toJsStringLiteralArg(mapper, listOf(rawPath))
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(arg).doesNotContain("\n").doesNotContain("\r").doesNotContain("\u0000")
        assertThat(decodeArg<List<String>>(arg)).containsExactly(rawPath)
    }

    @Test
    fun `an empty file list round-trips`() {
        val arg = toJsStringLiteralArg(mapper, emptyList<String>())
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).isEmpty()
    }

    @Test
    fun `the boolean visibility flag round-trips for both values`() {
        assertThat(decodeArg<Boolean>(toJsStringLiteralArg(mapper, true))).isTrue()
        assertThat(decodeArg<Boolean>(toJsStringLiteralArg(mapper, false))).isFalse()
        assertThat(isSelfContainedStringLiteral(toJsStringLiteralArg(mapper, true))).isTrue()
    }

    @Test
    fun `a webview function call passes exactly one encoded argument`() {
        val name = """C:\Users\me\it's "a" name.png"""
        val script = webviewFunctionCall(mapper, "handleNativeDrop", listOf(name))
        assertThat(script).startsWith("window.handleNativeDrop(").endsWith(")")
        val arg = script.removePrefix("window.handleNativeDrop(").removeSuffix(")")
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).containsExactly(name)
    }

    @Test
    fun `a webview function call without arguments has an empty argument list`() {
        assertThat(webviewFunctionCall(mapper, "resetTopBarClicked")).isEqualTo("window.resetTopBarClicked()")
    }

    @Test
    fun `a webview function call encodes each argument separately`() {
        assertThat(webviewFunctionCall(mapper, "update", "a", true)).isEqualTo("""window.update("\"a\"","true")""")
    }

    @Test
    fun `a webview function call rejects a name that is not an identifier`() {
        for (functionName in listOf("", "a.b", "a();b", "1abc", "handleNativeDrop ")) {
            assertThatThrownBy { webviewFunctionCall(mapper, functionName, true) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `running a webview function call executes the encoded script and reports success`() {
        val scripts = mutableListOf<String>()
        val failures = mutableListOf<Exception>()

        val issued = runWebviewFunctionCall(mapper, "setDragAndDropVisible", arrayOf<Any>(true), { scripts += it }, { failures += it })

        assertThat(issued).isTrue()
        assertThat(scripts).containsExactly("""window.setDragAndDropVisible("true")""")
        assertThat(failures).isEmpty()
    }

    @Test
    fun `running a webview function call reports failure when the script cannot be executed`() {
        val executionError = IllegalStateException("browser is disposed")
        val failures = mutableListOf<Exception>()

        val issued = runWebviewFunctionCall(mapper, "resetTopBarClicked", emptyArray<Any>(), { throw executionError }, { failures += it })

        assertThat(issued).isFalse()
        assertThat(failures).containsExactly(executionError)
    }

    @Test
    fun `running a webview function call propagates an invalid function name without executing anything`() {
        val scripts = mutableListOf<String>()
        val failures = mutableListOf<Exception>()

        assertThatThrownBy { runWebviewFunctionCall(mapper, "window.foo", arrayOf<Any>(true), { scripts += it }, { failures += it }) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(scripts).isEmpty()
        assertThat(failures).isEmpty()
    }

    @Test
    fun `running a webview function call propagates an argument that cannot be serialized without executing anything`() {
        val scripts = mutableListOf<String>()
        val failures = mutableListOf<Exception>()

        // Jackson cannot serialize a plain Object because it has no properties (FAIL_ON_EMPTY_BEANS).
        assertThatThrownBy { runWebviewFunctionCall(mapper, "handleNativeDrop", arrayOf(Any()), { scripts += it }, { failures += it }) }
            .isInstanceOf(JsonMappingException::class.java)
        assertThat(scripts).isEmpty()
        assertThat(failures).isEmpty()
    }
}
