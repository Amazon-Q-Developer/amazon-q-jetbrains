// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.q.core.ClientConnectionSettings
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.AwsClientManager
import software.amazon.q.jetbrains.core.Resource
import software.amazon.q.jetbrains.core.region.AwsRegionProvider
import java.time.Duration

/**
 * Save Amazon Q Profile Resource Cache
 */
object QProfileResources {
    /**
     * save available Q Profile list as cache with default duration 60 s。
     */
    val LIST_REGION_PROFILES = object : Resource.Cached<List<QRegionProfile>>() {
        override val id: String = "amazonq.allProfiles"

        override fun fetch(connectionSettings: ClientConnectionSettings<*>): List<QRegionProfile> {
            val failedRegions = mutableListOf<String>()
            var lastException: Exception? = null

            val mappedProfiles = QEndpoints.listRegionEndpoints().flatMap { (regionKey, _) ->
                val awsRegion = AwsRegionProvider.getInstance()[regionKey] ?: return@flatMap emptyList()
                val client = AwsClientManager
                    .getInstance()
                    .getClient<CodeWhispererRuntimeClient>(connectionSettings.withRegion(awsRegion))

                try {
                    val profiles = client.listAvailableProfilesPaginator {}
                        .profiles()
                        .map { p -> QRegionProfile(arn = p.arn(), profileName = p.profileName() ?: "<no name>") }
                    LOG.debug { "Found profiles for region $regionKey : $profiles" }

                    profiles
                } catch (e: Exception) {
                    // Don't abort the whole listing when a single region fails (e.g. a 403 from a
                    // firewall-blocked endpoint). Log it, remember it, and continue to the next region
                    // so profiles from reachable regions are still returned. We only surface an error
                    // when every region fails (checked below), matching the VS Code extension behavior.
                    LOG.warn(e) { "Failed to list Q profiles for region $regionKey" }
                    failedRegions.add(regionKey)
                    lastException = e

                    emptyList()
                }
            }

            // If no profiles could be listed in any region and at least one region failed, surface the
            // failure instead of returning an empty list (which callers treat as "user has no profiles").
            if (mappedProfiles.isEmpty() && failedRegions.isNotEmpty()) {
                LOG.warn { "Failed to list Q profiles for all attempted regions: $failedRegions" }
                lastException?.let { throw it }
                error("Failed to list Q profiles for regions: $failedRegions")
            }

            return mappedProfiles
        }

        override fun expiry(): Duration = Duration.ofSeconds(60)
    }

    private val LOG = getLogger<QProfileResources>()
}
