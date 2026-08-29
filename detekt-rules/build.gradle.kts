// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
}

// This is a detekt ruleset: it's loaded by the detekt CLI, which runs in the Gradle daemon JVM (JDK 21), NOT in
// the IDE. The 2026.2 profile would otherwise compile it to Java 25 bytecode via the shared jvm conventions, which
// the JDK-21 detekt runtime can't load (UnsupportedClassVersionError). Pin it to 21 regardless of IDE profile.
private val detektRulesJavaVersion = 21
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(detektRulesJavaVersion))
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    jvmToolchain(detektRulesJavaVersion)
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.test)
    testImplementation(libs.junit4)
    testImplementation(libs.assertj)

    // only used to make test work
    testRuntimeOnly(libs.slf4j.api)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.WARN
}
