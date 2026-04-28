// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.controller.chat.userIntent

import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent

class UserIntentRecognizer {
    fun getUserIntentFromPromptChatMessage(prompt: String) = when {
        prompt.startsWith("Explain") -> UserIntent.EXPLAIN_CODE_SELECTION
        prompt.startsWith("Refactor") -> UserIntent.SUGGEST_ALTERNATE_IMPLEMENTATION
        prompt.startsWith("Fix") -> UserIntent.APPLY_COMMON_BEST_PRACTICES
        prompt.startsWith("Optimize") -> UserIntent.IMPROVE_CODE
        prompt.startsWith("Generate unit tests") -> UserIntent.GENERATE_UNIT_TESTS
        else -> null
    }
}
