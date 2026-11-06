// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow.shortcut

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.mock

class ShellCommandShortcutActionPromoterTest {

    @Test
    fun `chat shortcut is preferred while chat has focus`() {
        val ideAction = ideAction("ReplaceInPath")
        val chatAction = chatAction()

        val promoted = promoter(chatFocused = true).promote(mutableListOf(ideAction, chatAction), context())

        assertThat(promoted).containsExactly(chatAction, ideAction)
    }

    @Test
    fun `ide action keeps priority while chat does not have focus`() {
        // Without this the chat shortcuts would shadow Replace in Files, Complete Current Statement
        // and Navigate to Last Edit Location across the whole IDE.
        val ideAction = ideAction("ReplaceInPath")
        val chatAction = chatAction()
        val input = mutableListOf(ideAction, chatAction)

        val promoted = promoter(chatFocused = false).promote(input, context())

        assertThat(promoted).containsExactly(ideAction, chatAction)
    }

    @Test
    fun `list is untouched when no chat shortcut is in contention`() {
        val first = ideAction("EditorCompleteStatement")
        val second = ideAction("ReplaceInPath")

        val promoted = promoter(chatFocused = true).promote(mutableListOf(first, second), context())

        assertThat(promoted).containsExactly(first, second)
    }

    @Test
    fun `relative order of non chat actions is preserved`() {
        val first = ideAction("EditorCompleteStatement")
        val second = ideAction("ReplaceInPath")
        val third = ideAction("JumpToLastChange")
        val chatAction = chatAction()

        val promoted = promoter(chatFocused = true)
            .promote(mutableListOf(first, second, chatAction, third), context())

        assertThat(promoted).containsExactly(chatAction, first, second, third)
    }

    @Test
    fun `promoting does not drop or duplicate actions`() {
        val ideAction = ideAction("ReplaceInPath")
        val chatAction = chatAction()
        val input = mutableListOf(ideAction, chatAction)

        val promoted = promoter(chatFocused = true).promote(input, context())

        assertThat(promoted).hasSameSizeAs(input)
        assertThat(promoted).containsExactlyInAnyOrderElementsOf(input)
    }

    private fun promoter(chatFocused: Boolean) = ShellCommandShortcutActionPromoter { chatFocused }

    // Unstubbed: the promoter only forwards whatever it finds to the injected focus predicate.
    private fun context(): DataContext = mock()

    private fun chatAction(): ShellCommandShortcutAction =
        object : ShellCommandShortcutAction(ShellCommandShortcut.BUTTON_STOP, "Stop") {}

    private fun ideAction(name: String): AnAction = object : AnAction(name) {
        override fun actionPerformed(e: AnActionEvent) = Unit
    }
}
