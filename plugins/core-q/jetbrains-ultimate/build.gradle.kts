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

// The community module bundles com.intellij.java / org.jetbrains.idea.maven, and IntelliJ Platform Gradle
// Plugin 2.16 now pulls their full transitive bundledPlugin/bundledModule closure (maven-jps, the intellij.java.*
// and fleet.* modules, etc.) onto the test-fixtures classpath. Those coordinates are tagged for the community
// SDK (IC-...), so when this IU module re-resolves the inherited fixtures against its own SDK they can't be
// found. Exclude just those two leaking plugins and their backend module transitives; the IU SDK still provides
// everything this module's own fixtures need (e.g. the JavaScript/NodeJS plugins it bundles directly).
configurations.testFixturesApi {
    exclude(group = "bundledPlugin", module = "com.intellij.java")
    exclude(group = "bundledPlugin", module = "org.jetbrains.idea.maven")
    exclude(group = "bundledPlugin", module = "com.intellij.gradle")
    exclude(group = "bundledPlugin", module = "com.intellij.properties")
    exclude(group = "bundledModule")
}
