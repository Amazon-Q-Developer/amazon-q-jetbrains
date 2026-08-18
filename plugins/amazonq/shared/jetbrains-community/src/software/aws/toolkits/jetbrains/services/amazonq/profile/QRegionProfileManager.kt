// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.q.core.TokenConnectionSettings
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.AwsClientManager
import software.amazon.q.jetbrains.core.AwsResourceCache
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sono.isSono
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.amazon.q.jetbrains.core.region.AwsRegionProvider
import software.amazon.q.jetbrains.settings.QSettingsMigrationUtil
import software.amazon.q.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle.message
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.time.Duration
import java.util.Collections
import kotlin.reflect.KClass

@Service(Service.Level.APP)
@State(name = "qProfileStates", storages = [Storage("amazonq.xml")])
class QRegionProfileManager : PersistentStateComponent<QProfileState>, Disposable {

    // Map to store connectionId to its active profile
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, QRegionProfile>(mutableMapOf())
    private val connectionIdToProfileCount = mutableMapOf<String, Int>()

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(
                BearerTokenProviderListener.TOPIC,
                object : BearerTokenProviderListener {
                    override fun invalidate(providerId: String) {
                        connectionIdToActiveProfile.remove(providerId)
                        connectionIdToProfileCount.remove(providerId)
                    }
                }
            )
    }

    /**
     * Called on project startup to validate if selected profile is still active
     */
    @Deprecated("This is a giant hack and we are not handling all the cases")
    @RequiresBackgroundThread
    fun validateProfile(project: Project) {
        val conn = getIdcConnectionOrNull(project)
        val selected = activeProfile(project) ?: return
        val profiles = try {
            listRegionProfiles(project)
        } catch (e: Exception) {
            if (e is AccessDeniedException) {
                null
            } else {
                // if we can't list profiles assume it is valid
                LOG.warn { "Continuing with $selected since listAvailableProfiles failed" }
                return
            }
        }

        // succeeded in listing profiles, but none match selected
        // profiles should be null if access denied or connection is not IdC
        if (profiles == null || profiles.none { it.arn == selected.arn }) {
            // Note that order matters, should switch to null first then invalidateProfile
            switchProfile(project, null, intent = QProfileSwitchIntent.Reload)
            invalidateProfile(selected.arn)
            Telemetry.amazonq.profileState.use { span ->
                span.source(QProfileSwitchIntent.Reload.value)
                    .amazonQProfileRegion(selected.region)
                    .ssoRegion(conn?.region)
                    .credentialStartUrl(conn?.startUrl)
                    .result(MetricResult.Failed)
            }
        }
    }

    fun listRegionProfiles(project: Project): List<QRegionProfile>? {
        val connection = getIdcConnectionOrNull(project) ?: return null

        return try {
            val connectionSettings = connection.getConnectionSettings()
            val mappedProfiles = AwsResourceCache.getInstance().getResourceNow(
                resource = QProfileResources.LIST_REGION_PROFILES,
                connectionSettings = connectionSettings,
                timeout = Duration.ofSeconds(30),
                useStale = true,
                forceFetch = false
            )
            if (mappedProfiles.size == 1) {
                switchProfile(project, mappedProfiles.first(), intent = QProfileSwitchIntent.Update)
            }
            mappedProfiles.takeIf { it.isNotEmpty() }?.also {
                connectionIdToProfileCount[connection.id] = it.size
            } ?: error("You don't have access to the resource")
        } catch (e: Exception) {
            if (e is AccessDeniedException) {
                LOG.warn { "Failed to list region profiles: ${e.message}" }
            } else {
                LOG.warn(e) { "Failed to list region profiles" }
            }

            throw e
        }
    }

    fun activeProfile(project: Project): QRegionProfile? = getIdcConnectionOrNull(project)?.let { connectionIdToActiveProfile[it.id] }

    fun hasValidConnectionButNoActiveProfile(project: Project): Boolean = getIdcConnectionOrNull(project) != null && activeProfile(project) == null

    fun switchProfile(project: Project, newProfile: QRegionProfile?, intent: QProfileSwitchIntent) {
        val conn = getIdcConnectionOrNull(project) ?: return

        val oldProfile = connectionIdToActiveProfile[conn.id]
        if (oldProfile == newProfile) return

        connectionIdToActiveProfile[conn.id] = newProfile
        LOG.debug { "Switch from profile $oldProfile to $newProfile for project ${project.name}" }

        if (newProfile != null) {
            if (intent == QProfileSwitchIntent.User || intent == QProfileSwitchIntent.Auth) {
                notifyInfo(
                    title = message("action.q.profile.usage.text"),
                    content = message("action.q.profile.usage", newProfile.profileName),
                    project = project
                )

                Telemetry.amazonq.didSelectProfile.use { span ->
                    span.source(intent.value)
                        .amazonQProfileRegion(newProfile.region)
                        .profileCount(connectionIdToProfileCount[conn.id])
                        .ssoRegion(conn.region)
                        .credentialStartUrl(conn.startUrl)
                        .result(MetricResult.Succeeded)
                }
            } else {
                Telemetry.amazonq.profileState.use { span ->
                    span.source(intent.value)
                        .amazonQProfileRegion(newProfile.region)
                        .ssoRegion(conn.region)
                        .credentialStartUrl(conn.startUrl)
                        .result(MetricResult.Succeeded)
                }
            }
        }

        ApplicationManager.getApplication().messageBus
            .syncPublisher(QRegionProfileSelectedListener.TOPIC)
            .onProfileSelected(project, newProfile)
    }

    private fun invalidateProfile(arn: String) {
        val updated = connectionIdToActiveProfile.filterValues { it != null && it.arn != arn }
        connectionIdToActiveProfile.clear()
        connectionIdToActiveProfile.putAll(updated)
    }

    // for each idc connection, user should have a profile, otherwise should show the profile selection error page
    fun isPendingProfileSelection(project: Project): Boolean = getIdcConnectionOrNull(project)?.let { conn ->
        val profileCounts = connectionIdToProfileCount[conn.id] ?: 0
        val activeProfile = connectionIdToActiveProfile[conn.id]
        profileCounts > 1 && activeProfile?.arn.isNullOrEmpty()
    } ?: false

    fun shouldDisplayProfileInfo(project: Project): Boolean = getIdcConnectionOrNull(project)?.let { conn ->
        (connectionIdToProfileCount[conn.id] ?: 0) > 1
    } ?: false

    fun getQClientSettings(project: Project, profile: QRegionProfile?): TokenConnectionSettings {
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        if (conn !is AwsBearerTokenConnection) {
            error("not a bearer connection")
        }

        val settings = conn.getConnectionSettings()
        val defaultRegion = AwsRegionProvider.getInstance()[QDefaultServiceConfig.REGION] ?: error("unknown region from Q default service config")
        val regionId = profile?.region ?: activeProfile(project)?.region
        val awsRegion = regionId?.let { AwsRegionProvider.getInstance()[it] } ?: defaultRegion

        return settings.withRegion(awsRegion)
    }

    inline fun <reified T : SdkClient> getQClient(project: Project): T = getQClient(project, null, T::class)
    inline fun <reified T : SdkClient> getQClient(project: Project, profile: QRegionProfile): T = getQClient(project, profile, T::class)

    fun <T : SdkClient> getQClient(project: Project, profile: QRegionProfile?, sdkClass: KClass<T>): T {
        val settings = getQClientSettings(project, profile)
        val client = AwsClientManager.getInstance().getClient(sdkClass, settings)
        return client
    }

    fun getIdcConnectionOrNull(project: Project): AwsBearerTokenConnection? {
        val manager = ToolkitConnectionManager.getInstance(project)
        val connection = manager.activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        val state = manager.connectionStateForFeature(QConnection.getInstance())

        return if (connection != null && !connection.isSono() && state == BearerTokenAuthState.AUTHORIZED) {
            connection
        } else {
            null
        }
    }

    companion object {
        private val LOG = getLogger<QRegionProfileManager>()
        fun getInstance(): QRegionProfileManager = service<QRegionProfileManager>()
    }

    /**
     * Set when the language server reports that RTS has blocked Q Developer plugin access for this
     * identity. Holds the service's own message, which is shown verbatim: FEATURE_NOT_SUPPORTED is
     * reused across several RTS gates, so only the message says why and what to do about it.
     *
     * Deliberately in-memory rather than part of [QProfileState]. The whole sequence -- observe the
     * block, sign out, show the message on the login screen -- happens inside one IDE session, so
     * persistence buys only that the message survives a restart. That is not worth adding a field to
     * a persisted component shared with profile state: it changes that component's serialized shape
     * for every user. After a restart the user is signed out and sees the normal login screen; if they
     * sign in with the same identity the block is reported again within seconds.
     */
    // Volatile because the write comes from the language server's notification thread while the reads
    // happen on the EDT (prepareBrowser) and on pooled threads (handleListProfilesMessage). Without it
    // a reader may never observe the write, which would silently disable the whole feature.
    @Volatile
    var qDevAccessBlockedMessage: String? = null
        private set

    fun setQDevAccessBlocked(message: String) {
        qDevAccessBlockedMessage = message
    }

    /**
     * Recovery path, and required rather than optional: without it an identity that later becomes
     * eligible -- or one misclassified -- would be pinned to the blocked screen with no way out.
     */
    fun clearQDevAccessBlocked() {
        qDevAccessBlockedMessage = null
    }

    override fun dispose() {}

    override fun getState(): QProfileState {
        val state = QProfileState()
        state.connectionIdToActiveProfile.putAll(this.connectionIdToActiveProfile)
        state.connectionIdToProfileList.putAll(this.connectionIdToProfileCount)
        return state
    }

    override fun loadState(state: QProfileState) {
        connectionIdToActiveProfile.clear()
        connectionIdToActiveProfile.putAll(state.connectionIdToActiveProfile)

        connectionIdToProfileCount.clear()
        connectionIdToProfileCount.putAll(state.connectionIdToProfileList)
    }

    override fun noStateLoaded() {
        val state = QSettingsMigrationUtil.migrateState(
            "qProfileStates",
            QProfileState::class.java
        ) ?: QProfileState()
        loadState(state)
    }
}

class QProfileState : BaseState() {
    @get:Property
    @get:MapAnnotation
    val connectionIdToActiveProfile by map<String, QRegionProfile>()

    @get:Property
    @get:MapAnnotation
    val connectionIdToProfileList by map<String, Int>()
}

/**
 * The [AccessDeniedException] reason meaning Amazon Q Developer is no longer accepting this
 * customer. Compared as a raw string rather than via the generated
 * [software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedExceptionReason] enum
 * because the bundled service model does not (yet) declare this value, so
 * [AccessDeniedException.reason] would resolve to `UNKNOWN_TO_SDK_VERSION` while
 * [AccessDeniedException.reasonAsString] still returns the real wire value.
 */
private const val FEATURE_NOT_SUPPORTED_REASON = "FEATURE_NOT_SUPPORTED"

/**
 * Whether [throwable] (or anything in its cause chain) is Amazon Q Developer permanently rejecting
 * this identity because it is no longer accepting new customers.
 *
 * This is a deliberate, permanent rejection rather than a transient failure, so callers should
 * surface it distinctly instead of offering a retry that can never succeed.
 *
 * The match is intentionally narrow: it requires an actual [AccessDeniedException] whose reason is
 * exactly [FEATURE_NOT_SUPPORTED_REASON]. Other modeled reasons -- notably
 * `UNAUTHORIZED_CUSTOMIZATION_RESOURCE_ACCESS`, `UNAUTHORIZED_WORKSPACE_CONTEXT_FEATURE_ACCESS`
 * and `TEMPORARILY_SUSPENDED` -- must NOT match, the last especially since it IS transient and
 * needs to keep its retry affordance.
 *
 * The cause chain is walked because the exception surfaces through the resource cache, which may
 * wrap it; a visited set guards against a self-referential or cyclic chain.
 */
fun isQDeveloperNotAcceptingNewCustomers(throwable: Throwable): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = throwable
    while (current != null && seen.add(current)) {
        if (current is AccessDeniedException && current.reasonAsString() == FEATURE_NOT_SUPPORTED_REASON) {
            return true
        }
        current = current.cause
    }
    return false
}
