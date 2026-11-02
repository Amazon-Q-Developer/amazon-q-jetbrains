// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatController

class InlineChatFileListenerTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private val fileEditorManager = mock<FileEditorManager>()

    @Before
    fun setUp() {
        projectRule.project.replaceService(FileEditorManager::class.java, fileEditorManager, disposableRule.disposable)
    }

    @Test
    fun `constructor does not read the selected editor`() {
        createListener()

        verify(fileEditorManager, never()).selectedTextEditorWithRemotes
    }

    @Test
    fun `attaches to the selected editor only once`() {
        val selectionModel = mock<SelectionModel>()
        selectEditors(editor(selectionModel))
        val listener = createListener()

        runInEdtAndWait {
            listener.attachToSelectedEditor()
            listener.attachToSelectedEditor()
        }

        verify(selectionModel, times(1)).addSelectionListener(any())
    }

    @Test
    fun `keeps the editor that a selection change attached first`() {
        val selectedModel = mock<SelectionModel>()
        val switchedToModel = mock<SelectionModel>()
        selectEditors(editor(selectedModel))
        val listener = createListener()

        runInEdtAndWait {
            listener.selectionChanged(event(editor(switchedToModel)))
            listener.attachToSelectedEditor()
        }

        verify(switchedToModel).addSelectionListener(any())
        verify(selectedModel, never()).addSelectionListener(any())
    }

    @Test
    fun `attaches on a later selection change when no editor is selected`() {
        selectEditors()
        val selectionModel = mock<SelectionModel>()
        val listener = createListener()

        runInEdtAndWait {
            listener.attachToSelectedEditor()
            listener.selectionChanged(event(editor(selectionModel)))
        }

        verify(selectionModel).addSelectionListener(any())
    }

    private fun createListener(): InlineChatFileListener {
        val listener = InlineChatFileListener(projectRule.project, mock<InlineChatController>())
        // Dispose on the EDT, as on project close: disposing hides the inline chat hint.
        Disposer.register(disposableRule.disposable) { runInEdtAndWait { Disposer.dispose(listener) } }
        return listener
    }

    private fun selectEditors(vararg editors: Editor) {
        whenever(fileEditorManager.selectedTextEditorWithRemotes).thenReturn(arrayOf(*editors))
    }

    private fun editor(selectionModel: SelectionModel) = mock<Editor> {
        on { this.selectionModel } doReturn selectionModel
    }

    private fun event(editor: Editor): FileEditorManagerEvent {
        val textEditor = mock<TextEditor> { on { this.editor } doReturn editor }
        return mock { on { newEditor } doReturn textEditor }
    }
}
