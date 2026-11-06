// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.settings.MeetQSettings

class BrowserTest {
    @Test
    fun `chat prompt acknowledgements include old and deprecation settings`() {
        val settings = MeetQSettings().apply {
            disclaimerAcknowledged = true
            pairProgrammingAcknowledged = true
            deprecationNoticeAcknowledged = true
        }

        assertThat(chatPromptAcknowledgements(settings)).isEqualTo(
            ChatPromptAcknowledgements(
                disclaimerAcknowledged = true,
                pairProgrammingAcknowledged = true,
                deprecationNoticeAcknowledged = true,
            )
        )
    }
}
