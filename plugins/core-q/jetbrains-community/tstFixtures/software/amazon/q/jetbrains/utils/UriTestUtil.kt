// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.utils

import java.io.File

fun normalizeFileUri(uri: String): String {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        return uri
    }

    if (!uri.startsWith("file:///")) {
        return uri
    }

    val path = uri.substringAfter("file:///")
    val drive = File("/").absoluteFile.toURI().path.trim('/')
    return "file:///$drive/$path"
}
