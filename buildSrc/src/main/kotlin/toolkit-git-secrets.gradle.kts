// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import de.undercouch.gradle.tasks.download.Download
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("de.undercouch.download")
}

val downloadGitSecrets = tasks.register<Download>("downloadGitSecrets") {
    src("https://raw.githubusercontent.com/awslabs/git-secrets/master/git-secrets")
    dest("$buildDir/git-secrets")
    onlyIfModified(true)
    useETag(true)
}

// Gradle 9 removed exec {} inside doLast, requiring separate Exec tasks.
val registerGitSecrets = tasks.register<Exec>("registerGitSecrets") {
    onlyIf { !DefaultNativePlatform.getCurrentOperatingSystem().isWindows }
    dependsOn(downloadGitSecrets)
    workingDir(project.rootDir)
    commandLine("git", "config", "--add", "secrets.allowed", "123456789012")
}

val registerAwsPatterns = tasks.register<Exec>("registerAwsPatterns") {
    onlyIf { !DefaultNativePlatform.getCurrentOperatingSystem().isWindows }
    dependsOn(downloadGitSecrets)
    workingDir(project.rootDir)
    val path = "$buildDir${File.pathSeparator}"
    environment["PATH"] = path + (environment["PATH"] ?: "")
    commandLine("/bin/sh", "$buildDir/git-secrets", "--register-aws")
}

val gitSecretsCheck = tasks.register<Exec>("gitSecrets") {
    onlyIf { !DefaultNativePlatform.getCurrentOperatingSystem().isWindows }
    dependsOn(registerGitSecrets, registerAwsPatterns)
    workingDir(project.rootDir)
    val path = "$buildDir${File.pathSeparator}"
    environment["PATH"] = path + (environment["PATH"] ?: "")
    commandLine("/bin/sh", "$buildDir/git-secrets", "--scan")
}

tasks.findByName("check")?.let {
    it.dependsOn(gitSecretsCheck)
}
