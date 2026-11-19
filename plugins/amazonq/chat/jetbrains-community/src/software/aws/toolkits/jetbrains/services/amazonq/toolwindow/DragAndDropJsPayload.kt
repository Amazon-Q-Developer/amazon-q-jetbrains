// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Renders [value] as a single JavaScript string-literal expression (including the surrounding quotes)
 * whose runtime value is the JSON serialization of [value].
 *
 * The chat webview's `handleNativeDrop`, `handleNativeNotify` and `setDragAndDropVisible` handlers each
 * `JSON.parse()` their argument, so the argument has to reach them as one fully escaped JS string literal.
 * Building that literal by wrapping the JSON text in quotes and interpolating the value is unsafe: JSON
 * string escaping does not escape the `'` character, and a dropped file's name is attacker-controlled, so a
 * name containing a single quote could close the literal early and have its remainder run as script in the
 * privileged chat webview.
 *
 * Serializing the JSON document a second time yields a double-quoted JS string literal in which `'`, `"`,
 * `\` and control characters are all escaped, so no value inside it can terminate the literal. U+2028 and
 * U+2029 are escaped explicitly too: they are valid JavaScript line terminators that are illegal unescaped
 * inside a string literal on engines older than ES2019, and the JSON serializer leaves them raw.
 *
 * The encoding is loss-free: every value round-trips through the handler's `JSON.parse` unchanged, including
 * names the previous single-quoted construction corrupted — a Windows path such as `C:\Users` lost its
 * backslashes. Callers pass the result straight into the existing `executeJavaScript` calls.
 */
internal fun toJsStringLiteralArg(mapper: ObjectMapper, value: Any): String =
    mapper.writeValueAsString(mapper.writeValueAsString(value))
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
