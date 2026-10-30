// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams

object WorkspaceTrust {
    /** `aws/updateConfiguration` section the language server reads workspace trust changes from. */
    const val CONFIGURATION_SECTION = "aws.q.workspaceTrust"

    /** Trust state reported to the language server, which starts local MCP servers only when trusted. */
    fun state(project: Project): String = if (TrustedProjects.isProjectTrusted(project)) "trusted" else "restricted"

    fun updateParams(project: Project) = UpdateConfigurationParams(
        section = CONFIGURATION_SECTION,
        settings = mapOf("state" to state(project)),
    )
}

/**
 * Sends the project's trust state to the language server whenever the IDE changes it.
 */
class WorkspaceTrustHandler(private val project: Project, private val cs: CoroutineScope) : Disposable {
    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            TrustedProjectsListener.TOPIC,
            object : TrustedProjectsListener {
                override fun onProjectTrusted(project: Project) = onTrustChanged(project)

                override fun onProjectUntrusted(project: Project) = onTrustChanged(project)
            }
        )
        // The state can change between building the initialize request and this subscription
        sendState()
    }

    private fun onTrustChanged(changed: Project) {
        if (changed == project) {
            sendState()
        }
    }

    private fun sendState() {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { server ->
                server.updateConfiguration(WorkspaceTrust.updateParams(project))
            }
        }
    }

    override fun dispose() {}
}
