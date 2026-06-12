// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.models.Coordinates
import software.aws.toolkits.gradle.ciOnly
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

val ideProfile = IdeVersions.ideProfile(project)

dependencies {
    intellijPlatform {
        platformDependency(Coordinates(groupId = "com.jetbrains.intellij.rd", artifactId = "rd-platform"))
        // Required for collaboration auth credentials in 2025.3+
        val version = IdeVersions.ideProfile(project).ultimate.sdkVersion
        if (version.startsWith("2025.3")) {
            bundledModule("intellij.platform.collaborationTools.auth.base")
            bundledModule("intellij.platform.collaborationTools.auth")
        }
    }

    implementation(project(":plugin-core-q"))

    compileOnlyApi(project(":plugin-core-q:jetbrains-community"))

    // CodeWhispererTelemetryService uses a CircularFifoQueue
    implementation(libs.commons.collections)
    implementation(libs.nimbus.jose.jwt)
    api(libs.lsp4j)

    testFixturesApi(testFixtures(project(":plugin-core-q:jetbrains-community")))

    testImplementation(project(":plugin-core-q:jetbrains-community"))
}

sourceSets {
    test {
        java.srcDirs(
            findFolders(project(":plugin-core-q:jetbrains-community").project, "tst", ideProfile).map {
                project(":plugin-core-q:jetbrains-community").project.file(it)
            }
        )
        resources.srcDirs(
            findFolders(project(":plugin-core-q:jetbrains-community").project, "tst-resources", ideProfile)
                .map {
                    project(":plugin-core-q:jetbrains-community").project.file(it)
                }
        )
    }
}

tasks.test {
    // Run each test class in its own JVM on CI. The IntelliJ platform "kernel" (Fleet Transactor) is a
    // single application-scoped service shared by every test in a JVM. If a ProjectEntity Rete observer
    // left running by one test throws (a platform-internal ClassCastException in ProjectIdsStorage), the
    // test framework rethrows the logged error, which cancels the kernel's coroutine scope and tears it
    // down for the rest of the JVM — cascading into ClosedSendChannelException for every later test that
    // opens a project.
    //
    // forkEvery = 1 (a fresh JVM per class) makes cross-class contamination impossible and deterministic,
    // rather than merely smaller: a larger batch size would leave run-to-run flakiness, since Gradle does
    // not guarantee stable class ordering, so which classes share a JVM with a poisoner can shift between
    // runs. The cost is one platform boot per class; acceptable here and scoped to this module (the only
    // one that hits the cascade) and to CI so local dev is unaffected.
    ciOnly {
        forkEvery = 1
    }
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
