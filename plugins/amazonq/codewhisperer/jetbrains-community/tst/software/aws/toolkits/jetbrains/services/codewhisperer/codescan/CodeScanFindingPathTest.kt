// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.io.path.Path

class CodeScanFindingPathTest {
    @Rule
    @JvmField
    val projectRoot = TemporaryFolder()

    private val projectName = "demo-project"

    private fun root() = projectRoot.root.toPath().toAbsolutePath().normalize()

    @Test
    fun `resolves a finding nested inside the project root`() {
        val resolved = resolveScanFindingFile(root().toString(), projectName, "$projectName/src/main/App.kt")

        val file = checkNotNull(resolved) { "The finding was not resolved" }
        // Path.startsWith / endsWith are component-wise and do not touch the filesystem.
        assertThat(file.startsWith(root())).isTrue()
        assertThat(file.endsWith(Path("src", "main", "App.kt"))).isTrue()
    }

    @Test
    fun `resolves a path that has no project-name prefix`() {
        // substringAfter returns the whole string when the project name is absent, so the finding
        // is still resolved relative to the project root.
        val resolved = resolveScanFindingFile(root().toString(), projectName, "src/App.kt")

        val file = checkNotNull(resolved) { "The finding was not resolved" }
        assertThat(file.startsWith(root())).isTrue()
    }

    @Test
    fun `keeps a path whose dot-dot segments stay inside the root`() {
        val resolved = resolveScanFindingFile(root().toString(), projectName, "$projectName/src/../src/App.kt")

        assertThat(resolved).isEqualTo(root().resolve(Path("src", "App.kt")))
    }

    @Test
    fun `rejects a path that resolves outside the root via dot-dot`() {
        val outsidePath = "$projectName/../../../outside/secret"

        // Control: the unchecked resolution the old code performed resolved outside the project root.
        val naive = Path(root().toString(), outsidePath.substringAfter(projectName)).toAbsolutePath().normalize()
        assertThat(naive.startsWith(root())).isFalse()

        // The hardened resolver refuses to map the out-of-root path to a file.
        assertThat(resolveScanFindingFile(root().toString(), projectName, outsidePath)).isNull()
    }

    @Test
    fun `rejects a sibling directory that shares the root name prefix`() {
        // "<root>/../<rootName>-sibling/secret" normalizes to a sibling of the root. A component-wise
        // containment check must reject it; a plain string-prefix check would wrongly accept it.
        val resolved = resolveScanFindingFile(
            root().toString(),
            projectName,
            "$projectName/../${projectRoot.root.name}-sibling/secret"
        )

        assertThat(resolved).isNull()
    }

    @Test
    fun `rejects a path that resolves to the root itself`() {
        val resolved = resolveScanFindingFile(root().toString(), projectName, projectName)

        assertThat(resolved).isNull()
    }

    // --- isRealPathWithinRoot: symlink-aware write-sink guard (follows "..", ".", and symlinks) ---

    @Test
    fun `isRealPathWithinRoot accepts a real file inside the root`() {
        val proj = projectRoot.newFolder("project").toPath()
        val file = Files.createDirectories(proj.resolve("src")).resolve("App.kt")
        Files.writeString(file, "x")

        assertThat(isRealPathWithinRoot(proj.toString(), file.toString())).isTrue()
    }

    @Test
    fun `isRealPathWithinRoot rejects an absolute path outside the root`() {
        val proj = projectRoot.newFolder("project").toPath()
        val outside = projectRoot.newFolder("outside").toPath()
        val secret = outside.resolve("secret.txt")
        Files.writeString(secret, "x")

        assertThat(isRealPathWithinRoot(proj.toString(), secret.toString())).isFalse()
    }

    @Test
    fun `isRealPathWithinRoot rejects a dot-dot path that resolves outside the root`() {
        val proj = projectRoot.newFolder("project").toPath()
        val outside = projectRoot.newFolder("outside").toPath()
        Files.writeString(outside.resolve("secret.txt"), "x")

        // proj/../outside/secret.txt resolves to a real file outside the project root.
        val outsidePath = proj.resolve("..").resolve("outside").resolve("secret.txt").toString()
        assertThat(isRealPathWithinRoot(proj.toString(), outsidePath)).isFalse()
    }

    @Test
    fun `isRealPathWithinRoot accepts a symlink inside the root that points inside the root`() {
        val proj = projectRoot.newFolder("project").toPath()
        val target = Files.createDirectories(proj.resolve("real")).resolve("target.txt")
        Files.writeString(target, "x")
        val link = proj.resolve("good-link")
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: Exception) {
            Assume.assumeNoException("symlinks not supported on this platform", e)
        }

        assertThat(isRealPathWithinRoot(proj.toString(), link.toString())).isTrue()
    }

    @Test
    fun `isRealPathWithinRoot rejects a symlink inside the root that points outside the root`() {
        val proj = projectRoot.newFolder("project").toPath()
        val outside = projectRoot.newFolder("outside").toPath()
        val secret = outside.resolve("secret.txt")
        Files.writeString(secret, "x")
        val link = proj.resolve("outside-link")
        try {
            Files.createSymbolicLink(link, secret)
        } catch (e: Exception) {
            Assume.assumeNoException("symlinks not supported on this platform", e)
        }

        // The link lives inside the project, but its real target is outside — it must be rejected.
        assertThat(isRealPathWithinRoot(proj.toString(), link.toString())).isFalse()
    }

    @Test
    fun `isRealPathWithinRoot rejects a path that does not exist`() {
        val proj = projectRoot.newFolder("project").toPath()

        assertThat(isRealPathWithinRoot(proj.toString(), proj.resolve("missing.txt").toString())).isFalse()
    }

    @Test
    fun `isRealPathWithinRoot rejects the root itself`() {
        val proj = projectRoot.newFolder("project").toPath()

        assertThat(isRealPathWithinRoot(proj.toString(), proj.toString())).isFalse()
    }
}
