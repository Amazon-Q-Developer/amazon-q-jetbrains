// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyDescriptor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import migration.software.amazon.q.core.ToolkitClientManager
import migration.software.amazon.q.core.clients.SdkClientProvider
import migration.software.amazon.q.core.region.ToolkitRegionProvider
import migration.software.amazon.q.jetbrains.core.AwsResourceCache
import migration.software.amazon.q.jetbrains.core.RemoteResourceResolverProvider
import migration.software.amazon.q.jetbrains.core.coroutines.PluginCoroutineScopeTracker
import migration.software.amazon.q.jetbrains.core.credentials.CredentialManager
import migration.software.amazon.q.jetbrains.core.credentials.ToolkitAuthManager
import migration.software.amazon.q.jetbrains.core.credentials.profiles.ProfileWatcher
import migration.software.amazon.q.jetbrains.core.credentials.sso.SsoLoginCallbackProvider
import migration.software.amazon.q.jetbrains.settings.AwsSettings
import migration.software.amazon.q.jetbrains.telemetry.TelemetryService
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.runner.Description
import org.mockito.kotlin.mock
import software.amazon.q.jetbrains.core.DefaultRemoteResourceResolverProvider
import software.amazon.q.jetbrains.core.credentials.AwsConnectionManager
import software.amazon.q.jetbrains.core.credentials.CredentialsRegionHandler
import software.amazon.q.jetbrains.core.credentials.DefaultToolkitAuthManager
import software.amazon.q.jetbrains.core.credentials.DefaultToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.MockAwsConnectionManager
import software.amazon.q.jetbrains.core.credentials.MockCredentialsManager
import software.amazon.q.jetbrains.core.credentials.MockCredentialsRegionHandler
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
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
        if (!extensionArea.hasExtensionPoint("amazon.q.startupAuthFactory")) {
            extensionArea.registerExtensionPoint(
                "amazon.q.startupAuthFactory",
                "software.amazon.q.jetbrains.core.credentials.ToolkitStartupAuthFactory",
                ExtensionPoint.Kind.INTERFACE
            )
        }
        if (!extensionArea.hasExtensionPoint("amazon.q.sdk.clientCustomizer")) {
            extensionArea.registerExtensionPoint(
                "amazon.q.sdk.clientCustomizer",
                "software.amazon.q.core.ToolkitClientCustomizer",
                ExtensionPoint.Kind.INTERFACE
            )
        }
        if (!extensionArea.hasExtensionPoint("amazon.q.credentialProviderFactory")) {
            extensionArea.registerExtensionPoint(
                "amazon.q.credentialProviderFactory",
                "software.amazon.q.core.credentials.CredentialProviderFactory",
                ExtensionPoint.Kind.INTERFACE
            )
        }

        app.replaceService(AwsSettings::class.java, MockAwsSettings(), disposable)
        app.replaceService(ToolkitClientManager::class.java, mock<ToolkitClientManager>(), disposable)
        app.replaceService(TelemetryService::class.java, NoOpTelemetryService(), disposable)
        app.replaceService(ToolkitRegionProvider::class.java, AwsRegionProvider(), disposable)
        app.replaceService(CredentialManager::class.java, MockCredentialsManager(), disposable)
        app.replaceService(ToolkitAuthManager::class.java, DefaultToolkitAuthManager(), disposable)
        app.replaceService(AwsResourceCache::class.java, MockResourceCache(), disposable)
        app.replaceService(RemoteResourceResolverProvider::class.java, DefaultRemoteResourceResolverProvider(), disposable)
        app.replaceService(SsoLoginCallbackProvider::class.java, MockSsoLoginCallbackProvider(), disposable)
        app.replaceService(PluginCoroutineScopeTracker::class.java, PluginCoroutineScopeTracker(), disposable)
        app.replaceService(ProfileWatcher::class.java, mock<ProfileWatcher>(), disposable)
    }

    /**
     * Registers the project-scoped services that plugin.xml normally contributes via `<projectService>`.
     *
     * On 2026.2 the bare test project does not load the plugin descriptor, so `project.service<X>()` /
     * `replaceService` on the project fail with "Cannot find service" for project-scoped services. Call this
     * with the test's project (typically after [registerMissingServices], since some of these services resolve
     * application services in their constructors). It is a no-op-safe on 2025.x–2026.1.
     *
     * Register order matters: [CredentialsRegionHandler] must exist before [MockAwsConnectionManager] is
     * constructed (its `AwsConnectionManager` super-constructor resolves it), and the application services from
     * [registerMissingServices] (e.g. [AwsResourceCache], region provider) must already be present.
     */
    fun registerMissingProjectServices(project: Project, disposable: Disposable) {
        // Each plugin needs its own project-scoped coroutine-scope tracker (declared as a bare <projectService>).
        project.replaceService(PluginCoroutineScopeTracker::class.java, PluginCoroutineScopeTracker(), disposable)
        project.replaceService(CredentialsRegionHandler::class.java, MockCredentialsRegionHandler(), disposable)
        project.replaceService(AwsConnectionManager::class.java, MockAwsConnectionManager(project), disposable)
        project.replaceService(ToolkitConnectionManager::class.java, DefaultToolkitConnectionManager(project), disposable)
    }

    /**
     * Registers a real [SdkClientProvider] and replaces [ToolkitClientManager] with [MockClientManager], matching
     * the `testServiceImplementation` plugin.xml normally contributes. Heavy-platform tests that do
     * `service<ToolkitClientManager>() as MockClientManager` need this on 2026.2's bare test app. Call after
     * [registerMissingServices] (the EPs/region provider must exist first). No-op-safe on 2025.x–2026.1.
     */
    fun registerMockClientManager(disposable: Disposable) {
        val app = ApplicationManager.getApplication()
        // SdkClientProvider must exist before MockClientManager()'s AwsClientManager super-ctor resolves it.
        app.replaceService(SdkClientProvider::class.java, AwsSdkClient(), disposable)
        app.replaceService(ToolkitRegionProvider::class.java, AwsRegionProvider(), disposable)
        app.replaceService(ToolkitClientManager::class.java, MockClientManager(), disposable)
    }

    /**
     * Registers the `<registryKey>` entries that plugin.xml normally contributes.
     *
     * On 2026.2 the bare test application does not load the plugin descriptor, so these keys are
     * undefined and `Registry.is(...)`/`Registry.get(...)` throw "Registry key ... is not defined".
     * The keys are merged into the platform's contributed-key map (existing keys are preserved), so
     * this is a no-op on 2025.x–2026.1 where the descriptor already provides them.
     */
    fun registerMissingRegistryKeys() {
        Registry.mutateContributedKeys { existing ->
            val merged = LinkedHashMap(existing)
            CONTRIBUTED_REGISTRY_KEYS.forEach { (name, descriptor) ->
                merged.putIfAbsent(name, descriptor)
            }
            merged
        }
    }

    // Mirrors the <registryKey> entries declared in plugins/amazonq/src/main/resources/META-INF/plugin.xml.
    // RegistryKeyDescriptor(name, defaultValue, description, restartRequired, overrides, pluginId, pluginDescriptorPath)
    private val CONTRIBUTED_REGISTRY_KEYS: Map<String, RegistryKeyDescriptor> = listOf(
        descriptor("amazon.q.endpoint", "", "Endpoint to use for Amazon Q", restartRequired = true),
        descriptor("amazon.q.endpoints.json", "", "List of region-endpoint pairs in JSON array form", restartRequired = true),
        descriptor(
            "inline.completion.rem.dev.use.rhizome",
            "false",
            "Defined by IntelliJ. Used for Amazon Q to display suggestions on remote.",
            restartRequired = true
        ),
        descriptor("amazon.q.flare.endpoint", "", "Endpoint to use to download flare artifacts"),
        descriptor("aws.dev.useDAG", "false", "True if DAG should be used instead of authorization_grant with PKCE", overrides = true),
        descriptor(
            "aws.telemetry.endpoint",
            "https://client-telemetry.us-east-1.amazonaws.com",
            "Endpoint to use for publishing AWS client-side telemetry",
            restartRequired = true,
            overrides = true
        ),
        descriptor(
            "aws.telemetry.identityPool",
            "us-east-1:820fd6d1-95c0-4ca4-bffb-3f01d32da842",
            "Cognito identity pool to use for publishing AWS client-side telemetry",
            restartRequired = true,
            overrides = true
        ),
        descriptor(
            "aws.telemetry.region",
            "us-east-1",
            "Region to use for publishing AWS client-side telemetry",
            restartRequired = true,
            overrides = true
        ),
        descriptor(
            "aws.toolkit.developerMode",
            "false",
            "Enables features to facilitate development of the toolkit",
            overrides = true
        ),
        descriptor(
            "aws.toolkit.notification.endpoint",
            "https://idetoolkits-hostedfiles.amazonaws.com/Notifications/Jetbrains/combined/2.x.json",
            "Endpoint for AWS Toolkit notifications",
            restartRequired = true,
            overrides = true
        )
    ).associateBy { it.name }

    // RegistryKeyDescriptor's constructor arity changed across platforms: 2025.x takes
    // (name, defaultValue, description, restartRequired, overrides, pluginId); 2026.2 added a trailing
    // pluginDescriptorPath. Construct reflectively so this compiles and runs on every supported profile.
    private fun descriptor(
        name: String,
        defaultValue: String,
        description: String,
        restartRequired: Boolean = false,
        overrides: Boolean = false
    ): RegistryKeyDescriptor {
        val base = listOf(name, defaultValue, description, restartRequired, overrides, "amazon.q.test")
        val ctor = RegistryKeyDescriptor::class.java.declaredConstructors
            .first { it.parameterCount == 6 || it.parameterCount == 7 }
        val args = if (ctor.parameterCount == 7) base + null else base
        return ctor.newInstance(*args.toTypedArray()) as RegistryKeyDescriptor
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

/**
 * JUnit 4 rule that defines the plugin.xml `<registryKey>` entries (see [CoreTestHelper.registerMissingRegistryKeys]).
 * Extends [ApplicationRule] so the registry is loaded; place it before any rule/code that reads a registry key
 * (e.g. before [software.amazon.q.jetbrains.utils.rules.RegistryRule]) in a [com.intellij.testFramework.RuleChain].
 */
class CoreRegistryKeysRule : ApplicationRule() {
    override fun before(description: Description) {
        super.before(description)
        CoreTestHelper.registerMissingRegistryKeys()
    }
}

/**
 * JUnit 5 equivalent of [CoreRegistryKeysRule]. Register with `@ExtendWith(CoreRegistryKeysExtension::class)`.
 */
class CoreRegistryKeysExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        CoreTestHelper.registerMissingRegistryKeys()
    }
}
