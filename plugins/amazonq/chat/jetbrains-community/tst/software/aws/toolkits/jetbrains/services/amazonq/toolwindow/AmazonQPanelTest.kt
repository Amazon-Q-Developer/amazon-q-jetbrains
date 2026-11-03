// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.UIUtil
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AmazonQPanelTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
        unmockkStatic(JBCefApp::class)
    }

    @Test
    fun `shows the fallback message instead of failing when the JCEF classes are missing`() {
        // What the JVM throws when the bundled JCEF plugin is disabled and the class cannot be linked.
        mockkStatic(JBCefApp::class)
        every { JBCefApp.isSupported() } throws NoClassDefFoundError("com/intellij/ui/jcef/JBCefApp")

        // The startup activity builds this panel on every project open, even when the tool window is hidden.
        val panel = AmazonQPanel(projectRule.project, scope)
        Disposer.register(disposableRule.disposable, panel)

        val fallback = UIUtil.findComponentOfType(panel.component, JBTextArea::class.java)
        assertThat(fallback?.text).isEqualTo("JCEF not supported")
    }
}
