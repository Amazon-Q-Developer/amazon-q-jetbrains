// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// Browser.postChat serializes with Gson, so these tests assert the Gson output to pin the wire
// format. ChatCommunicationManager suppresses the same rule for the same reason.
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow.shortcut

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.wm.ToolWindow
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ShellCommandShortcutTest {

    // --- wire contract -------------------------------------------------------------------------
    // The chat UI is the shared web bundle, so these values are a contract with the chat client,
    // not an internal detail. Changing them silently breaks the shortcuts.

    @Test
    fun `notification method matches the shared chat client contract`() {
        assertThat(ShellCommandShortcut.EXECUTE_SHELL_COMMAND_SHORTCUT)
            .isEqualTo("aws/chat/executeShellCommandShortCut")
    }

    @Test
    fun `button ids match the shared chat client contract`() {
        assertThat(ShellCommandShortcut.BUTTON_RUN).isEqualTo("run-shell-command")
        assertThat(ShellCommandShortcut.BUTTON_REJECT).isEqualTo("reject-shell-command")
        assertThat(ShellCommandShortcut.BUTTON_STOP).isEqualTo("stop-shell-command")
    }

    @Test
    fun `ui message carries the button id under the id key`() {
        val message = ShellCommandShortcut.uiMessage(ShellCommandShortcut.BUTTON_STOP)

        assertThat(message.command).isEqualTo("aws/chat/executeShellCommandShortCut")
        assertThat(message.params).isEqualTo(mapOf("id" to "stop-shell-command"))
    }

    @Test
    fun `ui message is not scoped to a tab or request`() {
        // The chat client resolves the active tab and the message that owns the button itself, so
        // the host must not pin the notification to a tab.
        val message = ShellCommandShortcut.uiMessage(ShellCommandShortcut.BUTTON_RUN)

        assertThat(message.tabId).isNull()
        assertThat(message.requestId).isNull()
    }

    @Test
    fun `serialized notification has the json shape the chat client parses`() {
        // Browser.postChat serializes with Gson, and the chat client reads message.params.id.
        val json = Gson().toJson(ShellCommandShortcut.uiMessage(ShellCommandShortcut.BUTTON_REJECT))

        assertThat(json)
            .contains("\"command\":\"aws/chat/executeShellCommandShortCut\"")
            .contains("\"params\":{\"id\":\"reject-shell-command\"}")
    }

    @Test
    fun `each button id round trips through serialization`() {
        listOf(
            ShellCommandShortcut.BUTTON_RUN,
            ShellCommandShortcut.BUTTON_REJECT,
            ShellCommandShortcut.BUTTON_STOP,
        ).forEach { buttonId ->
            val json = Gson().toJson(ShellCommandShortcut.uiMessage(buttonId))
            val params = JsonParser.parseString(json).asJsonObject.getAsJsonObject("params")

            assertThat(params.get("id").asString).isEqualTo(buttonId)
        }
    }

    // --- focus predicate ----------------------------------------------------------------------
    // These chords are IntelliJ defaults too, so the predicate is what keeps the default keymap
    // working outside chat. Each false case below is a case where the IDE must keep its binding.

    @Test
    fun `shortcut is enabled when the chat window is available visible and active`() {
        assertThat(ShellCommandShortcut.isEnabledFor(toolWindow(available = true, visible = true, active = true)))
            .isTrue()
    }

    @Test
    fun `shortcut is disabled when the chat window is absent`() {
        assertThat(ShellCommandShortcut.isEnabledFor(null)).isFalse()
    }

    @Test
    fun `shortcut is disabled when the chat window is not active`() {
        // Chat is open but focus is in the editor, so Complete Current Statement, Replace in Files
        // and Navigate to Last Edit Location must still win.
        assertThat(ShellCommandShortcut.isEnabledFor(toolWindow(available = true, visible = true, active = false)))
            .isFalse()
    }

    @Test
    fun `shortcut is disabled when the chat window is hidden`() {
        assertThat(ShellCommandShortcut.isEnabledFor(toolWindow(available = true, visible = false, active = true)))
            .isFalse()
    }

    @Test
    fun `shortcut is disabled when the chat window is unavailable`() {
        assertThat(ShellCommandShortcut.isEnabledFor(toolWindow(available = false, visible = true, active = true)))
            .isFalse()
    }

    private fun toolWindow(available: Boolean, visible: Boolean, active: Boolean): ToolWindow = mock {
        on { isAvailable } doReturn available
        on { isVisible } doReturn visible
        on { isActive } doReturn active
    }
}
