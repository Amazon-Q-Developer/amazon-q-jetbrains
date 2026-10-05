// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow.shortcut

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project

/**
 * The chat shell-command chords are also bound by IntelliJ defaults: Complete Current Statement,
 * Replace in Files and Navigate to Last Edit Location. When several enabled actions share a
 * keystroke the IDE asks promoters which one wins.
 *
 * Prefer the chat action only while the chat window holds focus. Outside chat this returns the
 * list untouched, so the IDE keeps its own bindings.
 */
class ShellCommandShortcutActionPromoter internal constructor(
    private val isChatFocused: (Project?) -> Boolean,
) : ActionPromoter {

    // The platform instantiates extensions through a no-arg constructor.
    constructor() : this({ project -> ShellCommandShortcut.isChatFocused(project) })

    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val results = actions.toMutableList()

        if (results.none { it is ShellCommandShortcutAction }) return results
        if (!isChatFocused(context.getData(CommonDataKeys.PROJECT))) return results

        // Stable partition: chat shortcuts first, every other action keeps its relative order.
        results.sortBy { if (it is ShellCommandShortcutAction) 0 else 1 }
        return results
    }
}
