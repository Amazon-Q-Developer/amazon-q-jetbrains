// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class InlineChatControllerTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Test
    fun `reads the selected editor on the EDT when created off the EDT`() {
        val readOnEdt = CompletableFuture<Boolean>()
        val fileEditorManager = mock<FileEditorManager> {
            on { selectedTextEditorWithRemotes } doAnswer {
                readOnEdt.complete(ApplicationManager.getApplication().isDispatchThread)
                emptyArray<Editor>()
            }
        }
        projectRule.project.replaceService(FileEditorManager::class.java, fileEditorManager, disposableRule.disposable)
        val scope = CoroutineScope(SupervisorJob())

        try {
            assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
            val controller = InlineChatController(projectRule.project, scope)
            // Dispose on the EDT, as on project close.
            Disposer.register(disposableRule.disposable) { runInEdtAndWait { Disposer.dispose(controller) } }

            assertThat(readOnEdt.get(10, TimeUnit.SECONDS)).isTrue()
        } finally {
            scope.cancel()
        }
    }
}
