// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.q.jetbrains.core.CoreTestHelper
import software.amazon.q.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager

class CodeWhispererReferenceManagerTest {
    @Rule
    @JvmField
    var projectRule = PythonCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private val documentContentContent = "012345678\n9"
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var project: Project

    @Before
    fun setup() {
        // 262's bare test app doesn't load plugin.xml service registrations; register what getInstance(...) needs.
        // No-op on 251-261.
        CoreTestHelper.registerMissingServices(disposableRule.disposable)
        CoreTestHelper.registerMissingProjectServices(projectRule.project, disposableRule.disposable)
        fixture = projectRule.fixture
        project = projectRule.project

        fixture.configureByText("test.py", documentContentContent)
        runInEdtAndWait {
            fixture.editor.caretModel.moveToOffset(documentContentContent.length)
        }
    }

    @Test
    fun `test getReferenceLineNums return expected line numbers`() {
        val referenceManager = CodeWhispererCodeReferenceManager(project)
        assertThat(referenceManager.getReferenceLineNums(fixture.editor, 0, 1)).isEqualTo("1")
        assertThat(referenceManager.getReferenceLineNums(fixture.editor, 0, 10)).isEqualTo("1 to 2")
    }
}
