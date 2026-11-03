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
 * @return true if JCEF is present and usable in the current IDE. Call this instead of [JBCefApp.isSupported].
 *
 * Since 2026.2, JCEF ships as the bundled "Web Browser (JCEF)" plugin (`com.intellij.modules.jcef`).
 * When the user disables that plugin, or the product does not expose the `com.intellij.ui.jcef` classes
 * to plugins (e.g. DataGrip 2026.2), referencing [JBCefApp] throws [NoClassDefFoundError] at link time.
 * That is a [Throwable] but not an [Exception], so it must be caught explicitly rather than relying on
 * [JBCefApp.isSupported] returning false. This is the one place that checks JCEF availability; use other
 * JCEF classes only after it returns true.
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
