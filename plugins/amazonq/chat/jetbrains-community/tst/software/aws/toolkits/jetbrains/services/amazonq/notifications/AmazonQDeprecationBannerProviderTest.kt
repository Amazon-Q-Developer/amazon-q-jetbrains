// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AmazonQDeprecationBannerProviderTest {
    @Test
    fun `session state is visible until dismissed`() {
        val persistence = TestPersistence()
        val state = AmazonQDeprecationBannerSessionState(persistence)

        assertThat(state.shouldShow()).isTrue()

        state.dismiss()

        assertThat(state.shouldShow()).isFalse()
        assertThat(persistence.isDismissed()).isTrue()
    }

    @Test
    fun `persisted dismissal hides banner in a new session`() {
        val state = AmazonQDeprecationBannerSessionState(TestPersistence(dismissed = true))

        assertThat(state.shouldShow()).isFalse()
    }

    @Test
    fun `all eligible editors share visible state until dismissal`() {
        val state = AmazonQDeprecationBannerSessionState(TestPersistence())
        val firstProject = project()
        val secondProject = project()
        val firstFile = file()
        val secondFile = file()

        assertThat(shouldShowAmazonQDeprecationBanner(firstProject, firstFile, state)).isTrue()
        assertThat(shouldShowAmazonQDeprecationBanner(secondProject, secondFile, state)).isTrue()

        state.dismiss()

        assertThat(shouldShowAmazonQDeprecationBanner(firstProject, firstFile, state)).isFalse()
        assertThat(shouldShowAmazonQDeprecationBanner(secondProject, secondFile, state)).isFalse()
    }

    @Test
    fun `banner uses approved copy and destinations`() {
        assertThat(AMAZON_Q_DEPRECATION_HEADING)
            .isEqualTo("Amazon Q Developer IDE plugins: end of support")
        assertThat(AMAZON_Q_DEPRECATION_MESSAGE)
            .isEqualTo(
                "On April 30, 2027, AWS will discontinue support for Amazon Q Developer IDE plugins. " +
                    "For capabilities similar to Amazon Q Developer IDE plugins, explore Kiro to access the latest models and features, " +
                    "including agentic coding, chat and MCP support."
            )
        assertThat(AMAZON_Q_DEPRECATION_KIRO_URL).isEqualTo("https://kiro.dev")
        assertThat(AMAZON_Q_DEPRECATION_LEARN_MORE_URL)
            .isEqualTo("https://aws.amazon.com/blogs/devops/amazon-q-developer-end-of-support-announcement/")
    }

    @Test
    fun `eligible project file is shown`() {
        val state = AmazonQDeprecationBannerSessionState(TestPersistence())

        assertThat(
            shouldShowAmazonQDeprecationBanner(
                project = project(),
                file = file(),
                state = state,
            )
        ).isTrue()
    }

    @Test
    fun `dismissed session is not shown`() {
        val state = AmazonQDeprecationBannerSessionState(TestPersistence()).apply { dismiss() }

        assertThat(
            shouldShowAmazonQDeprecationBanner(
                project = project(),
                file = file(),
                state = state,
            )
        ).isFalse()
    }

    @Test
    fun `disposed project is not shown`() {
        assertThat(
            shouldShowAmazonQDeprecationBanner(
                project = project(disposed = true),
                file = file(),
                state = AmazonQDeprecationBannerSessionState(TestPersistence()),
            )
        ).isFalse()
    }

    @Test
    fun `default project is not shown`() {
        assertThat(
            shouldShowAmazonQDeprecationBanner(
                project = project(default = true),
                file = file(),
                state = AmazonQDeprecationBannerSessionState(TestPersistence()),
            )
        ).isFalse()
    }

    @Test
    fun `invalid file is not shown`() {
        assertThat(
            shouldShowAmazonQDeprecationBanner(
                project = project(),
                file = file(valid = false),
                state = AmazonQDeprecationBannerSessionState(TestPersistence()),
            )
        ).isFalse()
    }

    @Test
    fun `directory is not shown`() {
        assertThat(
            shouldShowAmazonQDeprecationBanner(
                project = project(),
                file = file(directory = true),
                state = AmazonQDeprecationBannerSessionState(TestPersistence()),
            )
        ).isFalse()
    }

    @Test
    fun `text editor is supported`() {
        assertThat(isSupportedEditor(mock<TextEditor>())).isTrue()
    }

    @Test
    fun `custom file editor is not supported`() {
        assertThat(isSupportedEditor(mock<FileEditor>())).isFalse()
    }

    private fun project(
        disposed: Boolean = false,
        default: Boolean = false,
    ) = mock<Project> {
        on { isDisposed } doReturn disposed
        on { isDefault } doReturn default
    }

    private fun file(
        valid: Boolean = true,
        directory: Boolean = false,
    ) = mock<VirtualFile> {
        on { isValid } doReturn valid
        on { isDirectory } doReturn directory
    }

    private class TestPersistence(
        private var dismissed: Boolean = false,
    ) : AmazonQDeprecationBannerPersistence {
        override fun isDismissed(): Boolean = dismissed

        override fun dismiss() {
            dismissed = true
        }
    }
}
