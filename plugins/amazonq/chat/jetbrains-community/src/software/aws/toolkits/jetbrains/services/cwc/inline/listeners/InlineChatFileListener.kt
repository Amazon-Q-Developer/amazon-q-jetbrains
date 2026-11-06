// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatController

class InlineChatFileListener(private val project: Project, private val controller: InlineChatController) : FileEditorManagerListener, Disposable {
    private var currentEditor: Editor? = null
    private var selectionListener: InlineChatSelectionListener? = null

    // Attaches to the editor that was selected before this listener was subscribed, unless selectionChanged already attached.
    @RequiresEdt
    fun attachToSelectedEditor() {
        if (currentEditor != null) return
        val editor = FileEditorManager.getInstance(project).selectedTextEditorWithRemotes.firstOrNull() ?: return
        setupListenersForEditor(editor)
        currentEditor = editor
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val newEditor = (event.newEditor as? TextEditor)?.editor ?: return
        if (newEditor != currentEditor) {
            currentEditor?.let { removeListenersFromCurrentEditor(it) }
            setupListenersForEditor(newEditor)
            currentEditor = newEditor
            controller.disposePopup(true)
        }
    }

    private fun setupListenersForEditor(editor: Editor) {
        selectionListener = InlineChatSelectionListener().also { listener ->
            editor.selectionModel.addSelectionListener(listener)
            Disposer.register(this, listener)
        }
    }

    private fun removeListenersFromCurrentEditor(editor: Editor) {
        selectionListener?.let { listener ->
            editor.selectionModel.removeSelectionListener(listener)
            Disposer.dispose(listener)
        }
        selectionListener = null
    }

    override fun dispose() {
        currentEditor?.let { removeListenersFromCurrentEditor(it) }
        currentEditor = null
    }
}
