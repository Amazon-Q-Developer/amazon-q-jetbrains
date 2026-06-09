// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependencyConfiguration
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

plugins {
    id("toolkit-intellij-plugin")
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

dependencies {
    intellijPlatform {
        val sdkVersion = toolkitIntelliJ.version().get()

        create(IntelliJPlatformType.IntellijIdeaCommunity, sdkVersion)
        jetbrainsRuntime()

        localPlugin(project(":plugin-amazonq"))
        plugin(toolkitIntelliJ.ideProfile().map { "aws.toolkit:2.19-${it.shortName}" })

        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.JUnit5)
    }

    // not sure why not plugin not resolving transitive deps
    testRuntimeOnly(project(":plugin-core"))
}

intellijPlatform {
    buildSearchableOptions.set(false)
}
