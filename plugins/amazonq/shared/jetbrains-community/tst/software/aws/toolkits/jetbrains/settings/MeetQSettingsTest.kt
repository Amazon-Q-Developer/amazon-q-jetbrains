// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.q.jetbrains.utils.xmlElement

class MeetQSettingsTest {
    @Test
    fun `deprecation acknowledgements are independent and disabled by default`() {
        val settings = MeetQSettings()

        assertThat(settings.deprecationNoticeAcknowledged).isFalse()

        settings.deprecationNoticeAcknowledged = true

        assertThat(settings.deprecationNoticeAcknowledged).isTrue()
        assertThat(settings.pairProgrammingAcknowledged).isFalse()
    }

    @Test
    fun `existing settings deserialize with deprecation notice unacknowledged`() {
        val element = xmlElement(
            """
                <component name="meetQPage">
                    <option name="pairProgrammingAcknowledged" value="true" />
                </component>
            """.trimIndent()
        )

        val state = XmlSerializer.deserialize(element, MeetQSettingsConfiguration::class.java)

        assertThat(state.pairProgrammingAcknowledged).isTrue()
        assertThat(state.deprecationNoticeAcknowledged).isFalse()
    }
}
