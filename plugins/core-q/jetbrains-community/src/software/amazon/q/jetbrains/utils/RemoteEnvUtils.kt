// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

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
 * @return true if JCEF is present and usable in the current IDE.
 *
 * Some products (e.g. DataGrip 2026.2) do not expose the `com.intellij.ui.jcef` classes to plugins.
 * Referencing [JBCefApp] there throws [NoClassDefFoundError] at link time, which is a [Throwable] but
 * not an [Exception], so it must be caught explicitly rather than relying on [JBCefApp.isSupported]
 * returning false. Kept in its own function so the risky class reference is isolated to one call site.
 */
private fun isJcefSupported() = try {
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
