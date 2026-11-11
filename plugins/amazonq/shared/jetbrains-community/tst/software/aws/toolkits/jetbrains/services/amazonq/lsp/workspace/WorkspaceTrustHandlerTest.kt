// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams
import java.util.concurrent.CompletableFuture

class WorkspaceTrustHandlerTest {
    private val project = mockk<Project>()
    private val otherProject = mockk<Project>()
    private val languageServer = mockk<AmazonQLanguageServer>()
    private val listener = slot<TrustedProjectsListener>()
    private val sent = mutableListOf<UpdateConfigurationParams>()
    private var trusted = false
    private lateinit var testScope: TestScope

    @BeforeEach
    fun setup() {
        mockkStatic(TrustedProjects::class)
        every { TrustedProjects.isProjectTrusted(project) } answers { trusted }

        val application = mockk<Application>()
        val messageBus = mockk<MessageBus>()
        val connection = mockk<MessageBusConnection>()
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns application
        every { application.messageBus } returns messageBus
        every { messageBus.connect(any<Disposable>()) } returns connection
        every { connection.subscribe(TrustedProjectsListener.TOPIC, capture(listener)) } just runs

        val lspService = mockk<AmazonQLspService>()
        every { project.serviceIfCreated<AmazonQLspService>() } returns lspService
        coEvery { lspService.executeIfRunning<Any?>(any()) } coAnswers {
            firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> Any?>().invoke(lspService, languageServer)
        }
        every { languageServer.updateConfiguration(capture(sent)) } returns CompletableFuture.completedFuture(mockk())

        testScope = TestScope()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun start(): WorkspaceTrustHandler {
        val handler = WorkspaceTrustHandler(project, testScope)
        testScope.advanceUntilIdle()
        return handler
    }

    @Test
    fun `sends the current state once when it starts`() {
        trusted = true

        start()

        assertThat(sent).containsExactly(
            UpdateConfigurationParams(section = "aws.q.workspaceTrust", settings = mapOf("state" to "trusted"))
        )
    }

    @Test
    fun `sends trusted when the IDE trusts the project`() {
        start()
        sent.clear()
        trusted = true

        listener.captured.onProjectTrusted(project)
        testScope.advanceUntilIdle()

        assertThat(sent.map { it.settings }).containsExactly(mapOf("state" to "trusted"))
    }

    @Test
    fun `sends restricted when the IDE moves the project to safe mode`() {
        trusted = true
        start()
        sent.clear()
        trusted = false

        listener.captured.onProjectUntrusted(project)
        testScope.advanceUntilIdle()

        assertThat(sent.map { it.settings }).containsExactly(mapOf("state" to "restricted"))
    }

    @Test
    fun `ignores trust changes of other projects`() {
        start()
        sent.clear()

        listener.captured.onProjectTrusted(otherProject)
        listener.captured.onProjectUntrusted(otherProject)
        testScope.advanceUntilIdle()

        assertThat(sent).isEmpty()
        verify(exactly = 0) { TrustedProjects.isProjectTrusted(otherProject) }
    }

    @Test
    fun `sends nothing when the language server is not running`() {
        every { project.serviceIfCreated<AmazonQLspService>() } returns null

        start()
        listener.captured.onProjectTrusted(project)
        testScope.advanceUntilIdle()

        assertThat(sent).isEmpty()
    }
}
