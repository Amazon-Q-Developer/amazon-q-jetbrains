// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import migration.software.amazon.q.core.ToolkitClientManager
import migration.software.amazon.q.core.region.ToolkitRegionProvider
import migration.software.amazon.q.jetbrains.core.AwsResourceCache
import migration.software.amazon.q.jetbrains.core.coroutines.PluginCoroutineScopeTracker
import migration.software.amazon.q.jetbrains.core.credentials.CredentialManager
import migration.software.amazon.q.jetbrains.core.credentials.sso.SsoLoginCallbackProvider
import migration.software.amazon.q.jetbrains.settings.AwsSettings
import migration.software.amazon.q.jetbrains.telemetry.TelemetryService
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.mockito.kotlin.mock
import software.amazon.q.jetbrains.core.credentials.MockCredentialsManager
import software.amazon.q.jetbrains.core.credentials.sso.MockSsoLoginCallbackProvider
import software.amazon.q.jetbrains.core.region.AwsRegionProvider
import software.amazon.q.jetbrains.services.telemetry.NoOpTelemetryService
import software.amazon.q.jetbrains.settings.MockAwsSettings

/**
 * Registers the core application services that were previously contributed by plugin.xml.
 *
 * The 2026.2 (262) platform builds unit tests on a bare application that no longer loads the plugin
 * descriptor, so `applicationService`/`testServiceImplementation` registrations are absent and
 * `service<X>()` (and `replaceService`, which requires an existing instance) fail with "Cannot find
 * service". Tests that rely on these services should call this from `@BeforeEach` with a
 * `@TestDisposable`; it is a no-op on 2025.x–2026.1 where the descriptor still provides them.
 */
object CoreTestHelper {
    fun registerMissingServices(disposable: Disposable) {
        val app = ApplicationManager.getApplication()

        val extensionArea = app.extensionArea
        if (!extensionArea.hasExtensionPoint("amazon.q.connection.pinned.feature")) {
            extensionArea.registerExtensionPoint(
                "amazon.q.connection.pinned.feature",
                "software.amazon.q.jetbrains.core.credentials.pinning.FeatureWithPinnedConnection",
                ExtensionPoint.Kind.INTERFACE
            )
        }

        app.replaceService(AwsSettings::class.java, MockAwsSettings(), disposable)
        app.replaceService(ToolkitClientManager::class.java, mock<ToolkitClientManager>(), disposable)
        app.replaceService(TelemetryService::class.java, NoOpTelemetryService(), disposable)
        app.replaceService(ToolkitRegionProvider::class.java, AwsRegionProvider(), disposable)
        app.replaceService(CredentialManager::class.java, MockCredentialsManager(), disposable)
        app.replaceService(AwsResourceCache::class.java, MockResourceCache(), disposable)
        app.replaceService(SsoLoginCallbackProvider::class.java, MockSsoLoginCallbackProvider(), disposable)
        app.replaceService(PluginCoroutineScopeTracker::class.java, PluginCoroutineScopeTracker(), disposable)
    }
}

/**
 * JUnit 4 rule that registers the missing core services (see [CoreTestHelper]) for the duration of the test.
 * Place it first in a [com.intellij.testFramework.RuleChain] so services exist before other rules resolve them.
 */
class CoreServicesRule : DisposableRule() {
    override fun before() {
        CoreTestHelper.registerMissingServices(disposable)
    }
}

/**
 * JUnit 5 equivalent of [CoreServicesRule]. Register with `@ExtendWith(CoreServicesExtension::class)`.
 */
class CoreServicesExtension : DisposableRule(), BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        CoreTestHelper.registerMissingServices(disposable)
    }

    override fun afterEach(context: ExtensionContext) {
        after()
    }
}
