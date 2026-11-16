// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer

private const val CODEMODERNIZER_TOOLBAR_GROUP_ID = "aws.toolkit.codemodernizer.toolbar"

/**
 * 2026.2's bare unit-test application doesn't load codetransform's <actions> from plugin.xml, so the toolbar action
 * group that [CodeModernizerBottomWindowPanelManager.createToolbar] looks up is absent and the `as ActionGroup` cast
 * throws NPE. Register an empty group under that id so the panel can be constructed in tests. No-op on 2025.1-2026.1
 * where the action is already registered.
 */
internal fun registerCodeModernizerToolbarGroup(disposable: Disposable) {
    val actionManager = ActionManager.getInstance()
    if (actionManager.getAction(CODEMODERNIZER_TOOLBAR_GROUP_ID) != null) return
    actionManager.registerAction(CODEMODERNIZER_TOOLBAR_GROUP_ID, DefaultActionGroup())
    Disposer.register(disposable) { actionManager.unregisterAction(CODEMODERNIZER_TOOLBAR_GROUP_ID) }
}
