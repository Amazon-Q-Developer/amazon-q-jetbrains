// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.utils

import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.jcef.JBCefApp
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RemoteEnvUtilsTest {
    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    @Before
    fun setUp() {
        mockkStatic(JBCefApp::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(JBCefApp::class)
    }

    @Test
    fun `isJcefSupported returns false when the JCEF classes are missing`() {
        every { JBCefApp.isSupported() } throws jcefClassesMissing()

        assertThat(isJcefSupported()).isFalse()
    }

    @Test
    fun `isJcefSupported returns what JCEF reports when JCEF is present`() {
        every { JBCefApp.isSupported() } returnsMany listOf(true, false)

        assertThat(isJcefSupported()).isTrue()
        assertThat(isJcefSupported()).isFalse()
    }

    @Test
    fun `isQWebviewsAvailable returns false when the JCEF classes are missing`() {
        every { JBCefApp.isSupported() } throws jcefClassesMissing()

        assertThat(isQWebviewsAvailable()).isFalse()
    }

    // What the JVM throws when the bundled JCEF plugin is disabled and the class cannot be linked.
    private fun jcefClassesMissing() = NoClassDefFoundError("com/intellij/ui/jcef/JBCefApp")
}
