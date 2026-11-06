// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// isJcefSupported() is the one allowed caller of JBCefApp.isSupported (see BannedPatternRule).
@file:Suppress("BannedPattern")
package software.amazon.q.jetbrains.utils

import com.intellij.idea.AppMode
import com.intellij.ui.jcef.JBCefApp
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.tryOrNull
import software.amazon.q.jetbrains.isDeveloperMode
import java.nio.file.Paths

/**
 * @return true if running in any type of remote environment
 */
fun isRunningOnRemoteBackend() = AppMode.isRemoteDevHost()

/**
 * @return true if running in a codecatalyst remote environment
 */
fun isCodeCatalystDevEnv() = System.getenv("__DEV_ENVIRONMENT_ID") != null

/**
 * @return low fidelity "is internal compute". is not exact and may fail at any time
 */
private val isInternalAmznLinuxCompute by lazy {
    tryOrNull {
        Paths.get("/apollo").exists()
    } ?: false
}

/**
 * Use instead of [JBCefApp.isSupported]. When JCEF is missing (Web Browser (JCEF) plugin disabled, or DataGrip 2026.2),
 * referencing [JBCefApp] throws [NoClassDefFoundError], which is not an [Exception].
 */
fun isJcefSupported(): Boolean = try {
    JBCefApp.isSupported()
} catch (_: Throwable) {
    false
}

/**
 * On remote, only enabled experimentally and for internal
 */
fun isQWebviewsAvailable() = isJcefSupported() && if (!isRunningOnRemoteBackend()) {
    true
} else {
    isDeveloperMode() || isInternalAmznLinuxCompute
}
