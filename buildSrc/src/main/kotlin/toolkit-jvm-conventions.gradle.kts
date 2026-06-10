// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import software.aws.toolkits.gradle.jvmTarget
import software.aws.toolkits.gradle.kotlinTarget

plugins {
    id("java")
    kotlin("jvm")
}

val javaVersion = project.jvmTarget().get()
val javaVersionInt = javaVersion.majorVersion.toInt()
java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersionInt.toString())
        languageVersion = KotlinVersion.fromVersion(project.kotlinTarget().get())
        apiVersion = KotlinVersion.fromVersion(project.kotlinTarget().get())
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        // Allow compiling against SDK jars built with newer Kotlin (e.g. 262 SDK uses Kotlin 2.4.0)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
    // jvmToolchain sets both the Java and Kotlin toolchain in one call.
    // We pin to Java 21 to prevent the IntelliJ Platform Gradle Plugin from
    // auto-detecting the platform's bundled JBR (Java 25 for 262) and requiring
    // it as the build JDK.
    jvmToolchain(javaVersionInt)
}
