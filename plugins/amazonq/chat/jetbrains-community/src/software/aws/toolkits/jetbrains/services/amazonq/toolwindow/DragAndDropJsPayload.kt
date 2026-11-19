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
 * Every value round-trips through the handler's `JSON.parse` unchanged. That includes Windows paths such as
 * `C:\Users\me\a.png`, which `JSON.parse` rejected when the JSON text was wrapped in single quotes.
 */
internal fun toJsStringLiteralArg(mapper: ObjectMapper, value: Any): String =
    mapper.writeValueAsString(mapper.writeValueAsString(value))
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

/**
 * Builds the script that calls the webview function `window.<functionName>` with [argument] encoded by
 * [toJsStringLiteralArg]. Build every webview call that passes data with this function.
 */
internal fun webviewFunctionCall(mapper: ObjectMapper, functionName: String, argument: Any): String {
    require(JS_IDENTIFIER.matches(functionName)) { "Not a JavaScript identifier: $functionName" }
    return "window.$functionName(${toJsStringLiteralArg(mapper, argument)})"
}
