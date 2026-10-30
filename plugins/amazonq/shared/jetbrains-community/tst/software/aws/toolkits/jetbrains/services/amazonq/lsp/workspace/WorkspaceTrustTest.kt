// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class WorkspaceTrustTest {
    private val project = mockk<Project>()

    @AfterEach
    fun tearDown() {
        unmockkStatic(TrustedProjects::class)
    }

    @Test
    fun `reports trusted for a trusted project`() {
        mockkStatic(TrustedProjects::class)
        every { TrustedProjects.isProjectTrusted(project) } returns true

        assertThat(WorkspaceTrust.state(project)).isEqualTo("trusted")
    }

    @Test
    fun `reports restricted for a project opened in safe mode`() {
        mockkStatic(TrustedProjects::class)
        every { TrustedProjects.isProjectTrusted(project) } returns false

        assertThat(WorkspaceTrust.state(project)).isEqualTo("restricted")
    }

    @Test
    fun `sends the state in the section the language server reads`() {
        mockkStatic(TrustedProjects::class)
        every { TrustedProjects.isProjectTrusted(project) } returns true

        val params = WorkspaceTrust.updateParams(project)

        assertThat(params.section).isEqualTo("aws.q.workspaceTrust")
        assertThat(params.settings).isEqualTo(mapOf("state" to "trusted"))
    }
}
