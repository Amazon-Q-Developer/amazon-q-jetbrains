// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException

object CustomizationConstants {
    private const val NO_ACCESS_TO_CUSTOMIZATION_MESSAGE = "Your account is not authorized to use CodeWhisperer Enterprise."
    private const val INVALID_CUSTOMIZATION_MESSAGE = "You are not authorized to access"

    val noAccessToCustomizationExceptionPredicate: (e: Exception) -> Boolean = { e ->
        if (e !is CodeWhispererRuntimeException) {
            false
        } else {
            e is AccessDeniedException && (e.message?.contains(NO_ACCESS_TO_CUSTOMIZATION_MESSAGE, ignoreCase = true) ?: false)
        }
    }

    val invalidCustomizationExceptionPredicate: (e: Exception) -> Boolean = { e ->
        if (e !is CodeWhispererRuntimeException) {
            false
        } else {
            e is AccessDeniedException && (e.message?.let { isInvalidCustomizationMessage(it) } ?: false)
        }
    }

    fun isInvalidCustomizationMessage(m: String): Boolean =
        m.contains(INVALID_CUSTOMIZATION_MESSAGE, ignoreCase = true) && m.contains(":customization/")
}
