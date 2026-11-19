// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class DragAndDropJsPayloadTest {
    private val mapper = jacksonObjectMapper()

    // A valid JS string-literal argument is ONE JSON string token and nothing else, so a strict parse must
    // consume all of it. The trailing tokens a string breakout would introduce make this parse fail, which is
    // what distinguishes inert data from injected code.
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
     * that quote is the final character. A single-quote breakout is exactly the case where it is not.
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
    fun `a single quote in a file name cannot break out of the literal`() {
        val malicious = "/tmp/x');window.alert(document.cookie);//.png"
        val arg = toJsStringLiteralArg(mapper, listOf(malicious))
        // The payload is inert data inside one self-contained literal ...
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        // ... and the exact name still reaches the webview, so nothing is dropped or corrupted.
        assertThat(decodeArg<List<String>>(arg)).containsExactly(malicious)
    }

    @Test
    fun `the previous single-quote-wrapped construction was not self-contained (red control)`() {
        val malicious = "/tmp/x');window.alert(1);//.png"
        // Reconstructs the pre-fix sink: wrapping the JSON text in single quotes.
        val vulnerable = "'" + mapper.writeValueAsString(listOf(malicious)) + "'"
        // Proves the oracle actually detects the defect this change removes.
        assertThat(isSelfContainedStringLiteral(vulnerable)).isFalse()
    }

    @Test
    fun `double quotes, angle brackets and backslashes are neutralised`() {
        val malicious = """/tmp/a"</script><img src=x onerror=alert(1)>\end.png"""
        val arg = toJsStringLiteralArg(mapper, listOf(malicious))
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(decodeArg<List<String>>(arg)).containsExactly(malicious)
    }

    @Test
    fun `JS line terminators U+2028 and U+2029 are escaped`() {
        val malicious = "/tmp/a\u2028\u2029b.png"
        val arg = toJsStringLiteralArg(mapper, listOf(malicious))
        assertThat(arg).doesNotContain("\u2028").doesNotContain("\u2029")
        assertThat(arg).contains("\\u2028").contains("\\u2029")
        assertThat(decodeArg<List<String>>(arg)).containsExactly(malicious)
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
        val file = File("/tmp/x'y\";drop.png")
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
        val malicious = "/tmp/a\n\r\u0000b.png"
        val arg = toJsStringLiteralArg(mapper, listOf(malicious))
        assertThat(isSelfContainedStringLiteral(arg)).isTrue()
        assertThat(arg).doesNotContain("\n").doesNotContain("\r").doesNotContain("\u0000")
        assertThat(decodeArg<List<String>>(arg)).containsExactly(malicious)
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
}
