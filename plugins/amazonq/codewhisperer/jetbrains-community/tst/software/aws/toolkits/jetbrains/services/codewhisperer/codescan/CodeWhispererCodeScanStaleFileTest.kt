// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.ClickListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.q.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.amazon.q.jetbrains.utils.rules.NotificationListenerRule
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.checkFileStillExists
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * A code issue keeps the [VirtualFile] from review time. These tests cover opening an issue after that
 * file was deleted, which is also what a rebuild that recreates the file looks like to the IDE.
 */
class CodeWhispererCodeScanStaleFileTest : CodeWhispererCodeScanTestBase(CodeInsightTestFixtureRule()) {
    @Rule
    @JvmField
    val notificationListener = NotificationListenerRule(projectRule, disposableRule.disposable)

    private lateinit var file: VirtualFile

    @Before
    fun createReviewedFile() {
        // createCodeScanIssue() points at lines 10-15, so the file needs at least that many lines.
        file = projectRule.fixture.addFileToProject(
            "reviewed.txt",
            (1..LINE_COUNT).joinToString("\n") { "val value$it = performOperation($it)" }
        ).virtualFile
    }

    @Test
    fun `checkFileStillExists returns true without a warning while the file exists`() {
        assertThat(checkFileStillExists(project, file)).isTrue()
        assertThat(notificationListener.notifications).isEmpty()
    }

    @Test
    fun `checkFileStillExists returns false and warns after the file is deleted`() {
        deleteReviewedFile()

        assertThat(checkFileStillExists(project, file)).isFalse()
        assertWarnedThatFileIsGone()
    }

    @Test
    fun `selecting an issue opens its file`() {
        selectInResultsView(createCodeScanIssue(project, file))

        assertThat(FileEditorManager.getInstance(project).openFiles).containsExactly(file)
        assertThat(notificationListener.notifications).isEmpty()
    }

    @Test
    fun `selecting an issue whose file was deleted warns instead of opening it`() {
        val issue = createCodeScanIssue(project, file)
        deleteReviewedFile()

        selectInResultsView(issue)

        assertThat(FileEditorManager.getInstance(project).openFiles).isEmpty()
        assertWarnedThatFileIsGone()
    }

    @Test
    fun `the file link of an issue whose file was deleted warns instead of opening it`() {
        val issue = createCodeScanIssue(project, file)
        val detailsPanel = runInEdtAndGet { CodeWhispererCodeScanIssueDetailsPanel(project, issue) }
        deleteReviewedFile()

        runInEdtAndWait {
            val editorPane = UIUtil.findComponentOfType(detailsPanel, JEditorPane::class.java)
            checkNotNull(editorPane) { "The issue details panel has no editor pane" }
            editorPane.fireHyperlinkUpdate(
                HyperlinkEvent(editorPane, HyperlinkEvent.EventType.ACTIVATED, null, "amazonq://issue/openFile-${issue.findingId}")
            )
        }

        assertThat(FileEditorManager.getInstance(project).openFiles).isEmpty()
        assertWarnedThatFileIsGone()
    }

    @Test
    fun `double-clicking a reviewed file that was deleted warns instead of opening it`() {
        val filesPanel = runInEdtAndGet { CodeWhispererCodeScanHighlightingFilesPanel(project, listOf(file)) }
        deleteReviewedFile()

        runInEdtAndWait {
            val tree = UIUtil.findComponentOfType(filesPanel, Tree::class.java)
            checkNotNull(tree) { "The reviewed files panel has no tree" }
            val root = tree.model.root
            tree.selectionPath = TreePath(arrayOf(root, tree.model.getChild(root, 0)))
            doubleClick(tree)
        }

        assertThat(FileEditorManager.getInstance(project).openFiles).isEmpty()
        assertWarnedThatFileIsGone()
    }

    private fun selectInResultsView(issue: CodeWhispererCodeScanIssue) = runInEdtAndWait {
        val root = DefaultMutableTreeNode("root")
        val fileNode = DefaultMutableTreeNode(issue.file).also { root.add(it) }
        val issueNode = DefaultMutableTreeNode(issue).also { fileNode.add(it) }

        val tree = CodeWhispererCodeScanResultsView(project).getCodeScanTree()
        tree.model = CodeWhispererCodeScanTreeModel(root)
        tree.selectionPath = TreePath(arrayOf(root, fileNode, issueNode))
    }

    // Swing's own tree mouse handler needs a display, so drive only the platform ClickListener that the panel
    // installs. It counts presses itself and ignores points outside the component, so the tree needs a size
    // and two press/release pairs at the same time and place.
    private fun doubleClick(tree: Tree) {
        tree.setSize(TREE_SIZE, TREE_SIZE)
        val clickListeners = tree.mouseListeners.filter { it.javaClass.name.startsWith(ClickListener::class.java.name) }
        check(clickListeners.isNotEmpty()) { "The reviewed files tree has no ClickListener" }

        val time = System.currentTimeMillis()
        repeat(2) {
            clickListeners.forEach { listener -> listener.mousePressed(leftMouseEvent(tree, MouseEvent.MOUSE_PRESSED, time)) }
            clickListeners.forEach { listener -> listener.mouseReleased(leftMouseEvent(tree, MouseEvent.MOUSE_RELEASED, time)) }
        }
    }

    private fun leftMouseEvent(tree: Tree, id: Int, time: Long) =
        MouseEvent(tree, id, time, InputEvent.BUTTON1_DOWN_MASK, CLICK_POINT, CLICK_POINT, 1, false, MouseEvent.BUTTON1)

    private fun deleteReviewedFile() {
        WriteAction.runAndWait<IOException> { file.delete(this) }
        assertThat(file.isValid).isFalse()
    }

    private fun assertWarnedThatFileIsGone() {
        assertThat(notificationListener.notifications).hasSize(1)
        val notification = notificationListener.notifications.first()
        assertThat(notification.type).isEqualTo(NotificationType.WARNING)
        assertThat(notification.content).contains(file.path)
    }

    private companion object {
        const val LINE_COUNT = 20
        const val TREE_SIZE = 200
        const val CLICK_POINT = 5
    }
}
