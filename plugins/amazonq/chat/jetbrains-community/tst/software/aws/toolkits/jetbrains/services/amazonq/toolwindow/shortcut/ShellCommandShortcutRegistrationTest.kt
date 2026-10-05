// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow.shortcut

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The shortcut chords and class names live in plugin XML, which the compiler never checks. A typo
 * such as BACKSPACE instead of BACK_SPACE produces a plugin that loads with a dead shortcut, so
 * assert the registration here.
 */
class ShellCommandShortcutRegistrationTest {

    @Test
    fun `run shortcut is registered on both keymaps with the expected chords`() {
        assertShortcut(
            actionId = "aws.toolkit.jetbrains.services.amazonq.shellcommand.run",
            actionClass = RunShellCommandShortcutAction::class.java.name,
            macKeystroke = "meta shift ENTER",
            defaultKeystroke = "control shift ENTER",
        )
    }

    @Test
    fun `reject shortcut is registered on both keymaps with the expected chords`() {
        assertShortcut(
            actionId = "aws.toolkit.jetbrains.services.amazonq.shellcommand.reject",
            actionClass = RejectShellCommandShortcutAction::class.java.name,
            macKeystroke = "meta shift R",
            defaultKeystroke = "control shift R",
        )
    }

    @Test
    fun `stop shortcut is registered on both keymaps with the expected chords`() {
        assertShortcut(
            actionId = "aws.toolkit.jetbrains.services.amazonq.shellcommand.stop",
            actionClass = StopShellCommandShortcutAction::class.java.name,
            macKeystroke = "meta shift BACK_SPACE",
            defaultKeystroke = "control shift BACK_SPACE",
        )
    }

    @Test
    fun `promoter is registered so the chat shortcut can win a conflicting chord`() {
        val promoters = elements("actionPromoter").map { it.getAttribute("implementation") }

        assertThat(promoters).contains(ShellCommandShortcutActionPromoter::class.java.name)
    }

    @Test
    fun `each shortcut action is registered exactly once`() {
        val classes = elements("action").map { it.getAttribute("class") }
            .filter { it.contains("ShellCommandShortcutAction") }

        assertThat(classes).containsExactlyInAnyOrder(
            RunShellCommandShortcutAction::class.java.name,
            RejectShellCommandShortcutAction::class.java.name,
            StopShellCommandShortcutAction::class.java.name,
        )
    }

    private fun assertShortcut(actionId: String, actionClass: String, macKeystroke: String, defaultKeystroke: String) {
        val action = elements("action").single { it.getAttribute("id") == actionId }

        assertThat(action.getAttribute("class")).isEqualTo(actionClass)

        val byKeymap = elements("keyboard-shortcut", action)
            .associate { it.getAttribute("keymap") to it.getAttribute("first-keystroke") }

        // "Mac OS X 10.5+" is the keymap modern macOS IDEs actually use; the older id is kept for
        // parity with the other Amazon Q actions in this file.
        assertThat(byKeymap)
            .containsEntry("Mac OS X", macKeystroke)
            .containsEntry("Mac OS X 10.5+", macKeystroke)
            .containsEntry("\$default", defaultKeystroke)
    }

    private fun elements(tag: String, within: Element? = null): List<Element> {
        val nodes = within?.getElementsByTagName(tag) ?: pluginXml.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val pluginXml: Element by lazy {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(PLUGIN_XML)) {
            "$PLUGIN_XML is not on the test classpath"
        }
        stream.use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement
        }
    }

    companion object {
        private const val PLUGIN_XML = "META-INF/plugin-chat.xml"
    }
}
