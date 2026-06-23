// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IU)
}

dependencies {
    compileOnly(project(":plugin-core-q:jetbrains-community"))
    testFixturesApi(testFixtures(project(":plugin-core-q:jetbrains-community")))
}
// IC-tagged community bundled-plugin coords leaking into this IU test classpath are excluded centrally in toolkit-intellij-subplugin.
