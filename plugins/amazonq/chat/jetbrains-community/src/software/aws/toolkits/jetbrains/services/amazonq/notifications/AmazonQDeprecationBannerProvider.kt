// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import javax.swing.JComponent

internal const val AMAZON_Q_DEPRECATION_HEADING = "Amazon Q Developer IDE plugins: end of support"
internal const val AMAZON_Q_DEPRECATION_MESSAGE =
    "On April 30, 2027, AWS will discontinue support for Amazon Q Developer IDE plugins. " +
        "For capabilities similar to Amazon Q Developer IDE plugins, explore Kiro to access the latest models and features, " +
        "including agentic coding, chat and MCP support."

internal const val AMAZON_Q_DEPRECATION_KIRO_URL = "https://kiro.dev"
internal const val AMAZON_Q_DEPRECATION_LEARN_MORE_URL =
    "https://aws.amazon.com/blogs/devops/amazon-q-developer-end-of-support-announcement/"
internal const val AMAZON_Q_EDITOR_DEPRECATION_BANNER_DISMISSED_KEY =
    "amazon.q.editor.deprecation.banner.dismissed"

internal interface AmazonQDeprecationBannerPersistence {
    fun isDismissed(): Boolean

    fun dismiss()
}

private object PropertiesComponentAmazonQDeprecationBannerPersistence : AmazonQDeprecationBannerPersistence {
    override fun isDismissed(): Boolean =
        PropertiesComponent.getInstance().getBoolean(AMAZON_Q_EDITOR_DEPRECATION_BANNER_DISMISSED_KEY)

    override fun dismiss() {
        PropertiesComponent.getInstance().setValue(AMAZON_Q_EDITOR_DEPRECATION_BANNER_DISMISSED_KEY, true)
    }
}

internal class AmazonQDeprecationBannerSessionState(
    private val persistence: AmazonQDeprecationBannerPersistence = PropertiesComponentAmazonQDeprecationBannerPersistence,
) {
    private val dismissed = AtomicBoolean(persistence.isDismissed())

    fun shouldShow(): Boolean = !dismissed.get()

    fun dismiss() {
        if (dismissed.compareAndSet(false, true)) {
            persistence.dismiss()
        }
    }
}

private val sessionState by lazy { AmazonQDeprecationBannerSessionState() }

internal fun shouldShowAmazonQDeprecationBanner(
    project: Project,
    file: VirtualFile,
    state: AmazonQDeprecationBannerSessionState,
): Boolean =
    state.shouldShow() &&
        !project.isDisposed &&
        !project.isDefault &&
        file.isValid &&
        !file.isDirectory

internal fun isSupportedEditor(fileEditor: FileEditor): Boolean = fileEditor is TextEditor

class AmazonQDeprecationBannerProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!shouldShowAmazonQDeprecationBanner(project, file, sessionState)) return null

        return Function { fileEditor ->
            if (!isSupportedEditor(fileEditor) || !shouldShowAmazonQDeprecationBanner(project, file, sessionState)) {
                return@Function null
            }

            EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
                text = "<html><b>$AMAZON_Q_DEPRECATION_HEADING</b><br/>$AMAZON_Q_DEPRECATION_MESSAGE</html>"
                createActionLabel("Explore Kiro") {
                    BrowserUtil.browse(AMAZON_Q_DEPRECATION_KIRO_URL)
                }
                createActionLabel("Learn more") {
                    BrowserUtil.browse(AMAZON_Q_DEPRECATION_LEARN_MORE_URL)
                }
                setCloseAction {
                    sessionState.dismiss()
                    updateAllEditorNotifications()
                }
            }
        }
    }

    private fun updateAllEditorNotifications() {
        ProjectManager.getInstance().openProjects
            .filterNot { it.isDisposed || it.isDefault }
            .forEach { EditorNotifications.getInstance(it).updateAllNotifications() }
    }
}
