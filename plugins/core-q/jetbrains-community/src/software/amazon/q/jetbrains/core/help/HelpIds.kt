// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.help

enum class HelpIds(shortId: String, val url: String) {
    SETUP_CREDENTIALS(
        "setupCredentials",
        "https://docs.aws.amazon.com/console/toolkit-for-jetbrains/credentials"
    ),

    // CodeWhisperer
    CODEWHISPERER_TOKEN(
        "CodeWhispererToken",
        "https://aws.amazon.com/codewhisperer"
    ),

    // TODO: update this
    CODEWHISPERER_LOGIN_YES_NO(
        "CodeWhispererLoginYesNoDialog",
        "https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/setup-credentials.html"
    ),

    // TODO: update this
    CODEWHISPERER_LOGIN_DIALOG(
        "CodeWhispererLoginDialog",
        "https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/setup-credentials.html"
    ),

    // TODO: update this
    TOOLKIT_ADD_CONNECTIONS_DIALOG(
        "ToolkitAddConnectionsDialog",
        "https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/setup-credentials.html"
    ),

    Q_SWITCH_PROFILES_DIALOG(
        "QSwitchProfilesDialog",
        "https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/subscribe-understanding-profile.html"
    ),
    ;

    val id = "$HELP_ID_PREFIX.$shortId"
}

const val HELP_ID_PREFIX = "aws.toolkit"
