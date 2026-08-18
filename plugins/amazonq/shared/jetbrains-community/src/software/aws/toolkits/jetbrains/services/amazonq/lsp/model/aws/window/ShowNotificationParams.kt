// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.window

const val SHOW_NOTIFICATION_NOTIFICATION_METHOD = "aws/window/showNotification"

/**
 * Stable identifier the language server is expected to set on the access-blocked notification.
 * Preferred over matching on text, since it is the only part of the payload intended to be
 * machine-readable.
 */
const val Q_DEV_ACCESS_BLOCKED_NOTIFICATION_ID = "qDevPluginAccessBlocked"

/**
 * Payload of `aws/window/showNotification`.
 *
 * All fields are nullable with defaults because this is a generic channel shared by several servers:
 * a payload we do not recognise must deserialize without failing rather than break the connection.
 */
data class ShowNotificationParams(
    val id: String? = null,
    val type: Int? = null,
    val content: ShowNotificationContent? = null,
    val actions: List<ShowNotificationAction>? = null,
)

data class ShowNotificationContent(
    val title: String? = null,
    val text: String? = null,
)

data class ShowNotificationAction(
    val text: String? = null,
    val type: String? = null,
)
