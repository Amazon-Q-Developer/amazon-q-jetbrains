// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class NotificationManagerTest {

    // Open the project through a JUnit 5 fixture so its lifecycle (including teardown/close) is managed by the
    // framework. The previous `val projectRule = ProjectRule()` was never registered as an extension (a JUnit 4
    // rule that does not fire under JUnit 5), so the lazily-opened project was never closed and leaked a
    // ProjectImpl (surfaced as an engine-level "leaked instance of ProjectImpl" only in full-suite runs).
    private val projectFixture = projectFixture()
    private val project get() = projectFixture.get()

    @Test
    fun `If no follow-up actions, expand action is present`() {
        val sut = NotificationManager.createActions(project, listOf(), "Dummy Test Action", "Dummy title")
        assertThat(sut).isNotNull
        assertThat(sut).hasSize(1)
        assertThat(sut.first().title).isEqualTo("More...")
    }

    @Test
    fun `Show Url action shows the option to learn more`() {
        val followupActions = NotificationFollowupActions(
            "UpdateExtension",
            NotificationFollowupActionsContent(NotificationActionDescription("title", null))
        )
        val sut = NotificationManager.createActions(project, listOf(followupActions), "Dummy Test Action", "Dummy title")
        assertThat(sut).isNotNull
        assertThat(sut).hasSize(2)
        assertThat(sut.first().title).isEqualTo("Update")
        assertThat(sut[1].title).isEqualTo("More...")
    }
}
