// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.testFramework.utils.io.createDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.q.core.utils.ZIP_PROPERTY_POSIX
import software.amazon.q.core.utils.hasPosixFilePermissions
import software.amazon.q.core.utils.putNextEntry
import software.amazon.q.core.utils.test.assertPosixPermissions
import software.amazon.q.core.utils.writeText
import software.amazon.q.jetbrains.utils.satisfiesKt
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.setPosixFilePermissions

class LspUtilsTest {
    @Test
    fun `extractZipFile works`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }

        source.resolve("file1").writeText("contents1")
        source.resolve("file2").writeText("contents2")
        source.resolve("file3").writeText("contents3")

        val sourceZip = tempDir.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(sourceZip)).use { zip ->
            Files.walk(source).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .forEach {
                        zip.putNextEntry(source.relativize(it).toString(), it)
                    }
                val precedingSlashFile = source.resolve("file4").also { it.writeText("contents4") }
                zip.putNextEntry("/${source.relativize(precedingSlashFile)}", precedingSlashFile)
            }
        }

        extractZipFile(sourceZip, target)

        assertThat(target).satisfiesKt {
            val files = Files.list(it).use { stream -> stream.toList() }
            assertThat(files.size).isEqualTo(4)
            assertThat(target.resolve("file1")).hasContent("contents1")
            assertThat(target.resolve("file2")).hasContent("contents2")
            assertThat(target.resolve("file3")).hasContent("contents3")
            assertThat(target.resolve("file4")).hasContent("contents4")
        }
    }

    @Test
    fun `extractZipFile respects posix`(@TempDir tempDir: Path) {
        assumeTrue(tempDir.hasPosixFilePermissions())

        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }

        source.resolve("regularFile").also {
            it.writeText("contents1")
            it.setPosixFilePermissions(PosixFilePermissions.fromString("rw-r--r--"))
        }
        source.resolve("executableFile").also {
            it.writeText("contents2")
            it.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
        }

        val sourceZip = tempDir.resolve("source.zip")
        FileSystems.newFileSystem(
            sourceZip,
            mapOf(
                "create" to true,
                ZIP_PROPERTY_POSIX to true,
            )
        ).use { zipfs ->
            Files.walk(source).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .forEach { file ->
                        Files.copy(file, zipfs.getPath("/").resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES)
                    }
            }
        }

        extractZipFile(sourceZip, target)

        assertThat(target).satisfiesKt {
            val files = Files.list(it).use { stream -> stream.toList() }
            assertThat(files.size).isEqualTo(2)
            assertPosixPermissions(target.resolve("regularFile"), "rw-r--r--")
            assertPosixPermissions(target.resolve("executableFile"), "rwxr-xr-x")
        }
    }

    @Test
    fun `resolveZipEntryUnderRoot keeps a normal nested entry under the destination`(@TempDir tempDir: Path) {
        val root = tempDir.resolve("target")
        assertThat(resolveZipEntryUnderRoot(root, "sub/file.txt"))
            .isEqualTo(root.resolve("sub/file.txt").normalize())
    }

    @Test
    fun `resolveZipEntryUnderRoot keeps a leading-slash entry under the destination`(@TempDir tempDir: Path) {
        // Walking a zip from its root stringifies a top-level entry with a leading '/', which the real
        // archive relies on (see the `/file4` entry in `extractZipFile works`). It must stay inside.
        val root = tempDir.resolve("target")
        assertThat(resolveZipEntryUnderRoot(root, "/file4"))
            .isEqualTo(root.resolve("file4").normalize())
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects a parent-traversal entry`(@TempDir tempDir: Path) {
        val root = tempDir.resolve("target")
        // Red control: without the containment check, the same join escapes the destination.
        assertThat(Paths.get(root.toString(), "../evil.txt").normalize().startsWith(root.normalize())).isFalse()
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../evil.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects deep and leading-slash traversal`(@TempDir tempDir: Path) {
        val root = tempDir.resolve("target")
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../../../../../../etc/passwd") }
            .isInstanceOf(LspException::class.java)
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "/../../etc/passwd") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects a sibling whose name shares the destination prefix`(@TempDir tempDir: Path) {
        // `../target-evil` sits next to, not under, `target`; a string-prefix check would wrongly accept it,
        // but a component-wise startsWith does not.
        val root = tempDir.resolve("target")
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../target-evil/x.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot resolves a relative destination to an absolute path under it`() {
        // Covers the toAbsolutePath() handling: with a plain normalize() a relative root rejects every entry.
        val relativeRoot = Paths.get("build", "tmp", "zsroot-relative")
        val absoluteRoot = relativeRoot.toAbsolutePath().normalize()
        assertThat(resolveZipEntryUnderRoot(relativeRoot, "sub/file.txt"))
            .isEqualTo(absoluteRoot.resolve("sub/file.txt"))
        assertThatThrownBy { resolveZipEntryUnderRoot(relativeRoot, "../escape.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `extractZipFile aborts on a zip-slip entry and writes nothing outside the destination`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }
        source.resolve("good.txt").writeText("good")

        val sourceZip = tempDir.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(sourceZip)).use { zip ->
            zip.putNextEntry("good.txt", source.resolve("good.txt"))
            // A crafted entry name that tries to climb out of the extraction directory.
            zip.putNextEntry("../escaped.txt", source.resolve("good.txt"))
        }

        // Extraction aborts rather than writing outside the target. On current runtimes the JDK zip
        // filesystem rejects the `..` entry name itself (resolveZipEntryUnderRoot is the containment guard
        // for the entry shapes and older runtimes where zipfs does not); either way nothing escapes.
        // `tempDir` is the target's parent, i.e. where `../escaped.txt` would land.
        assertThatThrownBy { extractZipFile(sourceZip, target) }
            .isInstanceOf(LspException::class.java)
        assertThat(tempDir.resolve("escaped.txt")).doesNotExist()
    }
}
