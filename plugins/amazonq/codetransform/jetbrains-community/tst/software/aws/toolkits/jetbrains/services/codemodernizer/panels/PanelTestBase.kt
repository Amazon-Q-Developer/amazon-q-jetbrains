// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.panels

import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import software.amazon.q.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.services.codemodernizer.registerCodeModernizerToolbarGroup

open class PanelTestBase(
    @Rule @JvmField val projectRule: CodeInsightTestFixtureRule = CodeInsightTestFixtureRule(),
) {
    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    internal lateinit var project: Project

    @Before
    open fun setup() {
        project = projectRule.project
        // 262's bare test app doesn't load codetransform's toolbar <actions>; register the group so panels build.
        registerCodeModernizerToolbarGroup(disposableRule.disposable)
    }
}
