// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

val ideProfile = IdeVersions.ideProfile(project)

plugins {
    id("toolkit-intellij-plugin")
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
}

// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Add our source sets per IDE profile version (i.e. src-211)
sourceSets {
    main {
        java.srcDirs(findFolders(project, "src", ideProfile))
        resources.srcDirs(findFolders(project, "resources", ideProfile))
    }
    test {
        java.srcDirs(findFolders(project, "tst", ideProfile))
        resources.srcDirs(findFolders(project, "tst-resources", ideProfile))
    }
}

configurations {
    runtimeClasspath {
        // IDE provides Kotlin
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }

    configureEach {
        // IDE provides netty
        exclude("io.netty")

        if (name.startsWith("detekt")) {
            return@configureEach
        }

        // Exclude dependencies that ship with iDE
        exclude(group = "org.slf4j")
        if (!name.startsWith("kotlinCompiler") && !name.startsWith("generateModels") && !name.startsWith("rdGen")) {
            // we want kotlinx-coroutines-debug and kotlinx-coroutines-test
            exclude(group = "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
            exclude(group = "org.jetbrains.kotlinx", "kotlinx-coroutines-core")
        }

        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
                // Pin coroutines test tooling (-debug/-test/-bom) to the plain upstream version from Maven
                // Central. -core/-core-jvm are excluded above and come from the platform SDK instead, so the
                // intellij-flavored coroutines version is not needed here — and its -debug/-test artifacts
                // are not published, which would break testFixtures resolution.
                useVersion(versionCatalog.findVersion("kotlinCoroutines").get().toString())
                because("resolve kotlinx-coroutines version conflicts in favor of local version catalog")
            }

            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin")) {
                useVersion(versionCatalog.findVersion("kotlin").get().toString())
                because("resolve kotlin version conflicts in favor of local version catalog")
            }
        }
    }
}

tasks.processResources {
    // needed because both rider and ultimate include plugin-datagrip.xml which we are fine with
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.processTestResources {
    // TODO how can we remove this
    duplicatesStrategy = DuplicatesStrategy.WARN
}

// Run after the project has been evaluated so that the extension (intellijToolkit) has been configured
intellijPlatform {
    // Give each module its own, fully separate test sandbox. All amazonq submodules previously resolved to
    // the same top-level name ("plugin-amazonq") under one shared sandbox container, so every module's
    // prepareTestSandbox cleaned and rewrote the shared plugins-test/ directory with only its own plugin
    // descriptor. When the modules' test tasks interleave or are reordered, a module ends up running against
    // another's sandbox and its services fail to register ("Cannot find service ..."). Isolating the whole
    // sandbox container per module removes the shared mutable state entirely, which also resolves Gradle 9's
    // cross-module producer/consumer validation on the shared directory (no shared dir => no ordering needed).
    val moduleSandboxName = project.buildTreePath.replace(':', '-').trim('-')
    sandboxContainer.convention(project.layout.buildDirectory.dir("idea-sandbox/$moduleSandboxName"))
    instrumentCode = true
}

dependencies {
    intellijPlatform {
        val sdkVersion = toolkitIntelliJ.version().get()

        // annoying resolution issue that we don't want to bother fixing
        if (!project.name.contains("jetbrains-gateway")) {
            when (toolkitIntelliJ.ideFlavor.get()) {
                IdeFlavor.IU -> intellijIdeaUltimate(sdkVersion) { useInstaller.set(false) }
                IdeFlavor.RD -> rider(sdkVersion) { useInstaller.set(false) }
                else -> intellijIdeaCommunity(sdkVersion) { useInstaller.set(false) }
            }
        } else {
            create(IntelliJPlatformType.Gateway, sdkVersion)
        }

        bundledPlugins(toolkitIntelliJ.productProfile().map { it.bundledPlugins })
        plugins(toolkitIntelliJ.productProfile().map { it.marketplacePlugins })

        // OAuth modules split in 2025.3 (253) - must be explicitly bundled
        val profileName = providers.gradleProperty("ideProfileName").get()
        if (profileName >= "2025.3") {
            bundledModule("intellij.platform.collaborationTools")
            bundledModule("intellij.platform.collaborationTools.auth.base")
            bundledModule("intellij.platform.collaborationTools.auth")
        }

        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

// https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1844
tasks.withType<PrepareSandboxTask>().configureEach {
    disabledPlugins.addAll(
        "com.intellij.swagger",
        "org.jetbrains.plugins.kotlin.jupyter",
    )
}

tasks.jar {
    // :plugin-toolkit:jetbrains-community results in: --plugin-toolkit-jetbrains-community-IC-<version>.jar
    archiveBaseName.set(toolkitIntelliJ.ideFlavor.map { "${project.buildTreePath.replace(':', '-')}-$it" })
}

tasks.withType<Test>().configureEach {
    // conflict with Docker logging impl; so bypass service loader
    systemProperty("slf4j.provider", "org.slf4j.jul.JULServiceProvider")

    systemProperty("log.dir", intellijPlatform.sandboxContainer.map { "$it-test/logs" }.get())
    systemProperty("testDataPath", project.rootDir.resolve("testdata").absolutePath)
    systemProperty("org.gradle.project.ideProfileName", ideProfile.name)

    // Ensure enough coroutine scheduler threads for IntelliJ platform Transactor on resource-constrained CI runners (e.g. GitHub Actions with 2 cores)
    systemProperty("kotlinx.coroutines.scheduler.core.pool.size", "4")
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("aws.toolkits.enableTelemetry", false)
}
