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
// Note: the IC-tagged community bundled-plugin coordinates that leak into this IU module's test classpath are
// excluded centrally in the toolkit-intellij-subplugin convention (gated on the jetbrains-ultimate module name).
