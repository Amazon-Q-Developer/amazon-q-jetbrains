// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.utils.io.createDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
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
    fun `resolveZipEntryUnderRoot rejects a parent entry that resolves outside the destination`(@TempDir tempDir: Path) {
        val root = tempDir.resolve("target")
        // Control: without the containment check, the same join resolves outside the destination.
        assertThat(Paths.get(root.toString(), "../outside.txt").normalize().startsWith(root.normalize())).isFalse()
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../outside.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot reports a rejected entry with its own error code`(@TempDir tempDir: Path) {
        val error = catchThrowable { resolveZipEntryUnderRoot(tempDir.resolve("target"), "../outside.txt") }
        assertThat(error).isInstanceOf(LspException::class.java)
        assertThat(error.toString()).contains("[UNZIP_ENTRY_OUTSIDE_DESTINATION]")
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects a name that is not a valid path with the containment error code`(@TempDir tempDir: Path) {
        // No platform accepts NUL in a path, so Paths.get throws InvalidPathException for this name.
        val error = catchThrowable { resolveZipEntryUnderRoot(tempDir.resolve("target"), "a\u0000b.txt") }
        assertThat(error).isInstanceOf(LspException::class.java)
        assertThat(error.toString()).isEqualTo("LSP Error [UNZIP_ENTRY_OUTSIDE_DESTINATION]: Zip entry 'ab.txt' is not a valid path")
        assertThat(error.cause).isNull()
    }

    @Test
    fun `resolveZipEntryUnderRoot keeps line breaks and long names out of the exception message`(@TempDir tempDir: Path) {
        // A line break and a line separator, plus padding. Windows rejects the control character as an invalid
        // path, other platforms reject the entry for resolving outside the destination; both messages carry the
        // same truncated, filtered name.
        val entryName = "../a\nSECOND\u2028" + "x".repeat(500)
        val error = catchThrowable { resolveZipEntryUnderRoot(tempDir.resolve("target"), entryName) }
        assertThat(error).isInstanceOf(LspException::class.java)
        assertThat(error.toString()).contains("[UNZIP_ENTRY_OUTSIDE_DESTINATION]")
        assertThat(error.message)
            .contains("'../aSECOND${"x".repeat(88)}'")
            .doesNotContain("\n")
            .doesNotContain("\u2028")
            .doesNotContain("x".repeat(89))
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects deep and leading-slash parent entries`(@TempDir tempDir: Path) {
        val root = tempDir.resolve("target")
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../../../../../../outside/file.txt") }
            .isInstanceOf(LspException::class.java)
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "/../../outside/file.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects a sibling whose name shares the destination prefix`(@TempDir tempDir: Path) {
        // `../target-sibling` sits next to, not under, `target`; a string-prefix check would wrongly accept it,
        // but a component-wise startsWith does not.
        val root = tempDir.resolve("target")
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../target-sibling/x.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot resolves a relative destination to an absolute path under it`() {
        // Covers the toAbsolutePath() handling: the resolved path is absolute even when the destination is relative.
        val relativeRoot = Paths.get("build", "tmp", "zsroot-relative")
        val absoluteRoot = relativeRoot.toAbsolutePath().normalize()
        assertThat(resolveZipEntryUnderRoot(relativeRoot, "sub/file.txt"))
            .isEqualTo(absoluteRoot.resolve("sub/file.txt"))
        assertThatThrownBy { resolveZipEntryUnderRoot(relativeRoot, "../escape.txt") }
            .isInstanceOf(LspException::class.java)
    }

    @Test
    fun `extractZipFile aborts on an entry that resolves outside the destination and writes nothing outside it`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }
        source.resolve("good.txt").writeText("good")

        val sourceZip = tempDir.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(sourceZip)).use { zip ->
            zip.putNextEntry("good.txt", source.resolve("good.txt"))
            // An entry name that resolves outside the extraction directory.
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

    @Test
    fun `extractZipFile passes on the containment error code for an entry name that is not a valid path`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }
        source.resolve("good.txt").writeText("good")

        val sourceZip = tempDir.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(sourceZip)).use { zip ->
            zip.putNextEntry("a\u0000b.txt", source.resolve("good.txt"))
        }

        // The zip file system walks this name on every platform, so the containment check is what rejects it, and
        // extractZipFile must pass that rejection on with its own error code instead of wrapping it as UNZIP_FAILED.
        val error = catchThrowable { extractZipFile(sourceZip, target) }
        assertThat(error).isInstanceOf(LspException::class.java)
        assertThat(error.toString()).isEqualTo("LSP Error [UNZIP_ENTRY_OUTSIDE_DESTINATION]: Zip entry '/ab.txt' is not a valid path")
    }

    @Test
    fun `resolveZipEntryUnderRoot applies the platform meaning of a backslash`(@TempDir tempDir: Path) {
        // The zip file system keeps `\` as an ordinary character, so a walked entry can look like this.
        val root = tempDir.resolve("target")
        val entryName = "/..\\outside.txt"
        if (SystemInfo.isWindows) {
            // `\` separates segments on Windows, so the `..` segment leaves the destination.
            assertThatThrownBy { resolveZipEntryUnderRoot(root, entryName) }
                .isInstanceOf(LspException::class.java)
                .hasMessageContaining("resolves outside the extraction directory")
        } else {
            // Elsewhere `\` is part of the file name, so the entry stays in the destination.
            assertThat(resolveZipEntryUnderRoot(root, entryName)).isEqualTo(root.resolve("..\\outside.txt"))
        }
    }

    @Test
    fun `extractZipFile keeps a backslash entry name inside the destination`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }
        source.resolve("good.txt").writeText("good")

        val sourceZip = tempDir.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(sourceZip)).use { zip ->
            zip.putNextEntry("good.txt", source.resolve("good.txt"))
            zip.putNextEntry("..\\outside.txt", source.resolve("good.txt"))
        }

        if (SystemInfo.isWindows) {
            // The zip file system accepts this name, so the containment check is what stops the extraction.
            // extractZipFile must pass the check's exception on unchanged, keeping its own error code.
            assertThatThrownBy { extractZipFile(sourceZip, target) }
                .isInstanceOf(LspException::class.java)
                .hasMessageContaining("resolves outside the extraction directory")
                .hasToString("LSP Error [UNZIP_ENTRY_OUTSIDE_DESTINATION]: Zip entry '/..\\outside.txt' resolves outside the extraction directory")
        } else {
            extractZipFile(sourceZip, target)
            assertThat(target.resolve("..\\outside.txt")).hasContent("good")
        }
        assertThat(tempDir.resolve("outside.txt")).doesNotExist()
    }
}
