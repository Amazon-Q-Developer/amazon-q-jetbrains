// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.fasterxml.jackson.databind.ObjectMapper

private val JS_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

/**
 * Renders [value] as one JavaScript string-literal expression, including its surrounding double quotes, whose
 * runtime value is the JSON serialization of [value].
 *
 * The chat webview's `handleNativeDrop`, `handleNativeNotify` and `setDragAndDropVisible` handlers each
 * `JSON.parse()` their argument. Serializing the JSON document a second time yields a double-quoted literal in
 * which `"`, `\` and control characters are escaped. The serializer does not escape `'`, so the literal stays
 * self-contained only because its quotes are double; do not switch them to single quotes. U+2028 and U+2029 are
 * escaped explicitly: the serializer leaves them raw, and engines older than ES2019 reject them unescaped inside
 * a string literal.
 *
 * The values AmazonQPanel passes (strings, booleans, and lists of strings or files) round-trip through the
 * handler's `JSON.parse` unchanged. That includes Windows paths such as `C:\Users\me\a.png`, which `JSON.parse`
 * rejected when the JSON text was wrapped in single quotes.
 */
internal fun toJsStringLiteralArg(mapper: ObjectMapper, value: Any): String =
    mapper.writeValueAsString(mapper.writeValueAsString(value))
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

/**
 * Builds the script that calls the webview function `window.<functionName>`, passing each of [arguments] encoded by
 * [toJsStringLiteralArg]. The webview function must `JSON.parse` each argument (see the handlers in Browser.kt).
 * AmazonQPanel builds all of its webview calls with this function, so data always reaches the webview encoded.
 */
internal fun webviewFunctionCall(mapper: ObjectMapper, functionName: String, vararg arguments: Any): String {
    require(JS_IDENTIFIER.matches(functionName)) { "Not a JavaScript identifier: $functionName" }
    return arguments.joinToString(separator = ",", prefix = "window.$functionName(", postfix = ")") { toJsStringLiteralArg(mapper, it) }
}

/**
 * Builds the call with [webviewFunctionCall] and runs the script with [execute]. Returns false, after passing the
 * exception to [onFailure], when [execute] throws. An encoding error is not caught: an invalid function name or an
 * argument that cannot be serialized is a programming error, so it propagates to the caller instead of being logged
 * and swallowed.
 */
internal fun runWebviewFunctionCall(
    mapper: ObjectMapper,
    functionName: String,
    arguments: Array<out Any>,
    execute: (script: String) -> Unit,
    onFailure: (Exception) -> Unit,
): Boolean {
    val script = webviewFunctionCall(mapper, functionName, *arguments)
    return try {
        execute(script)
        true
    } catch (e: Exception) {
        onFailure(e)
        false
    }
}
