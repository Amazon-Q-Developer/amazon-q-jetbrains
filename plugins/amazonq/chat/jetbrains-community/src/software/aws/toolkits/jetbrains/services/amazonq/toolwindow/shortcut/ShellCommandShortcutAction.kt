// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow.shortcut

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AMAZON_Q_WINDOW_ID
import software.aws.toolkits.resources.AmazonQBundle

/**
 * Keyboard shortcuts for the run / reject / stop buttons that the chat UI renders while a shell
 * command is pending or running.
 *
 * The chat UI is the shared web bundle, so the shortcut is implemented the same way the VS Code
 * extension implements it: the host posts [EXECUTE_SHELL_COMMAND_SHORTCUT] to the chat UI, and the
 * chat client translates that into a click on the button whose id is supplied. The host does not
 * need to know which message or tab currently owns the button.
 */
object ShellCommandShortcut {
    /** Notification the chat UI listens for. Must match the shared chat client contract. */
    const val EXECUTE_SHELL_COMMAND_SHORTCUT = "aws/chat/executeShellCommandShortCut"

    const val BUTTON_RUN = "run-shell-command"
    const val BUTTON_REJECT = "reject-shell-command"
    const val BUTTON_STOP = "stop-shell-command"

    /** Builds the notification that asks the chat UI to click [buttonId]. */
    fun uiMessage(buttonId: String): FlareUiMessage = FlareUiMessage(
        command = EXECUTE_SHELL_COMMAND_SHORTCUT,
        params = mapOf("id" to buttonId),
    )

    /**
     * Whether a chat shortcut should act.
     *
     * These chords collide with IntelliJ defaults (Complete Current Statement, Replace in Files and
     * Navigate to Last Edit Location), so the actions stay disabled unless the chat window actually
     * holds focus. A disabled action lets the IDE fall through to its own binding, which keeps the
     * default keymap intact everywhere outside chat. This mirrors the `isFocus` guard the VS Code
     * extension puts on the same three shortcuts.
     */
    fun isEnabledFor(toolWindow: ToolWindow?): Boolean {
        if (toolWindow == null) return false
        return toolWindow.isAvailable && toolWindow.isVisible && toolWindow.isActive
    }

    /** Resolves the chat tool window, or null when it is absent or the project is gone. */
    fun chatToolWindow(project: Project?): ToolWindow? {
        if (project == null || project.isDisposed) return null
        return ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)
    }

    fun isChatFocused(project: Project?): Boolean = isEnabledFor(chatToolWindow(project))
}

/**
 * Posts a shell-command button click to the chat UI when the chat window has focus.
 */
abstract class ShellCommandShortcutAction(
    private val buttonId: String,
    text: String,
) : AnAction(text) {

    // isVisible/isActive read tool window UI state, which must happen on the EDT.
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = ShellCommandShortcut.isChatFocused(e.getData(CommonDataKeys.PROJECT))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        // update() already gates this, but actionPerformed is reachable directly (Find Action,
        // custom keymaps), so re-check rather than stealing the keystroke from the IDE.
        if (!ShellCommandShortcut.isChatFocused(project)) return

        val chat = ChatCommunicationManager.getInstance(project)
        // The tool window can be visible and focused while the webview is still loading. notifyUi
        // queues anything sent before the UI is ready and replays it on load, which for a shortcut
        // would click a button belonging to whichever command is pending by then. A keystroke is a
        // transient intent, so drop it instead of deferring it.
        if (!chat.uiReady.isCompleted) return

        chat.notifyUi(ShellCommandShortcut.uiMessage(buttonId))
    }
}

class RunShellCommandShortcutAction : ShellCommandShortcutAction(
    ShellCommandShortcut.BUTTON_RUN,
    AmazonQBundle.message("action.q.shellcommand.run.text"),
)

class RejectShellCommandShortcutAction : ShellCommandShortcutAction(
    ShellCommandShortcut.BUTTON_REJECT,
    AmazonQBundle.message("action.q.shellcommand.reject.text"),
)

class StopShellCommandShortcutAction : ShellCommandShortcutAction(
    ShellCommandShortcut.BUTTON_STOP,
    AmazonQBundle.message("action.q.shellcommand.stop.text"),
)
