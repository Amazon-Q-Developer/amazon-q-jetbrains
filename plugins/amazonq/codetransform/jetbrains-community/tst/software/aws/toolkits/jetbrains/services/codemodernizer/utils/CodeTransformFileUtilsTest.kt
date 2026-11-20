// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.util.SystemInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText

class CodeTransformFileUtilsTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    @Test
    fun `resolveZipEntryUnderRoot keeps a normal nested entry under the destination`() {
        val root = folder.newFolder("dest").toPath()
        assertThat(resolveZipEntryUnderRoot(root, "sources/Foo.java"))
            .isEqualTo(root.toAbsolutePath().normalize().resolve("sources/Foo.java"))
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects a parent entry that resolves outside the destination`() {
        val root = folder.newFolder("dest").toPath()
        // Control: the raw resolve the old code used resolves outside the destination.
        assertThat(root.resolve("../outside.txt").normalize().startsWith(root.toAbsolutePath().normalize())).isFalse()
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../outside.txt") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects absolute and sibling-prefix entries`() {
        val root = folder.newFolder("dest").toPath()
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "/outside/file.txt") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../dest-sibling/x.txt") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects an entry that resolves to the destination itself`() {
        val root = folder.newFolder("dest").toPath()
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "sources/..") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `resolveZipEntryUnderRoot applies the platform meaning of a backslash`() {
        val root = folder.newFolder("dest").toPath()
        if (SystemInfo.isWindows) {
            // `\` separates segments on Windows, so the `..` segment leaves the destination.
            assertThatThrownBy { resolveZipEntryUnderRoot(root, "..\\outside.txt") }
                .isInstanceOf(IllegalArgumentException::class.java)
        } else {
            // Elsewhere `\` is part of the file name, so the entry stays in the destination.
            assertThat(resolveZipEntryUnderRoot(root, "..\\outside.txt"))
                .isEqualTo(root.toAbsolutePath().normalize().resolve("..\\outside.txt"))
        }
    }

    @Test
    fun `resolveZipEntryUnderRoot rejects a name with control characters without leaking them`() {
        val root = folder.newFolder("dest").toPath()
        // Invalid-path branch (NUL): resolve() throws before the containment check; the message must still be sanitized.
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "a\u0000\nSECOND.txt") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageNotContaining("\u0000")
            .hasMessageNotContaining("\n")
        // Escape branch: a parent entry carrying a newline must also be sanitized in the thrown message.
        assertThatThrownBy { resolveZipEntryUnderRoot(root, "../a\nSECOND") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageNotContaining("\n")
    }

    @Test
    fun `unzipFile extracts a normal archive under the destination`() {
        val zip = folder.newFile("good.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            zos.putNextEntry(ZipEntry("sources/Foo.java"))
            zos.write("class Foo {}".toByteArray())
            zos.closeEntry()
        }
        val dest = folder.newFolder("out").toPath()

        assertThat(unzipFile(zip, dest)).isTrue()
        assertThat(dest.resolve("sources/Foo.java")).exists()
        assertThat(dest.resolve("sources/Foo.java").readText()).isEqualTo("class Foo {}")
    }

    @Test
    fun `unzipFile aborts on an entry that resolves outside the destination and writes nothing outside it`() {
        val zip = folder.newFile("outside.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            zos.putNextEntry(ZipEntry("good.txt"))
            zos.write("ok".toByteArray())
            zos.closeEntry()
            // java.util.zip.ZipFile returns this name verbatim (no normalization), so the guard must stop it.
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("outside".toByteArray())
            zos.closeEntry()
        }
        val dest = folder.newFolder("out").toPath()

        assertThatThrownBy { unzipFile(zip, dest) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dest.parent.resolve("escaped.txt").exists()).isFalse()
    }

    @Test
    fun `unzipFile with extractOnlySources rejects a source entry that escapes`() {
        val zip = folder.newFile("outside-sources.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            zos.putNextEntry(ZipEntry("sources/../../escaped.txt"))
            zos.write("outside".toByteArray())
            zos.closeEntry()
        }
        val dest = folder.newFolder("out").toPath()

        assertThatThrownBy { unzipFile(zip, dest, extractOnlySources = true) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dest.parent.resolve("escaped.txt").exists()).isFalse()
    }

    @Test
    fun `unzipFile with extractOnlySources extracts source entries and skips the others`() {
        val zip = folder.newFile("client-build.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            listOf("sources/Foo.java", "manifest.json").forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write("x".toByteArray())
                zos.closeEntry()
            }
        }
        val dest = folder.newFolder("out").toPath()

        assertThat(unzipFile(zip, dest, extractOnlySources = true)).isTrue()
        assertThat(dest.resolve("sources/Foo.java")).exists()
        assertThat(dest.resolve("manifest.json")).doesNotExist()
    }

    @Test
    fun `unzipFile with isSqlMetadata extracts the basename under the destination`() {
        val zip = folder.newFile("sql.zip").toPath()
        // A real zip separates folders with '/' on every platform, including Windows.
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            zos.putNextEntry(ZipEntry("nested/dir/meta.sct"))
            zos.write("S".toByteArray())
            zos.closeEntry()
        }
        val dest = folder.newFolder("out").toPath()

        assertThat(unzipFile(zip, dest, isSqlMetadata = true)).isTrue()
        assertThat(dest.toFile().list()).containsExactly("meta.sct")
    }

    @Test
    fun `unzipFile with isSqlMetadata flattens backslash entry names and skips backslash folder entries`() {
        val zip = folder.newFile("sql-backslash.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            // ZipEntry.isDirectory checks only for a trailing '/', so this folder entry reaches the flattening step.
            zos.putNextEntry(ZipEntry("nested\\dir\\"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("nested\\dir\\meta.sct"))
            zos.write("S".toByteArray())
            zos.closeEntry()
        }
        val dest = folder.newFolder("out").toPath()

        assertThat(unzipFile(zip, dest, isSqlMetadata = true)).isTrue()
        assertThat(dest.toFile().list()).containsExactly("meta.sct")
    }

    @Test
    fun `unzipFile writes no entry when one entry is rejected`() {
        val zip = folder.newFile("mixed.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            listOf("first.txt", "../outside.txt", "last.txt").forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write("x".toByteArray())
                zos.closeEntry()
            }
        }
        val dest = folder.newFolder("out").toPath()

        assertThatThrownBy { unzipFile(zip, dest) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dest.resolve("first.txt").exists()).isFalse()
        assertThat(dest.resolve("last.txt").exists()).isFalse()
        assertThat(dest.parent.resolve("outside.txt").exists()).isFalse()
    }

    @Test
    fun `extractSctMetadataFile returns the sct file from a metadata zip`() {
        val zip = folder.newFile("metadata.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            zos.putNextEntry(ZipEntry("project.sct"))
            zos.write("<project/>".toByteArray())
            zos.closeEntry()
        }
        val dest = folder.newFolder("sql")

        assertThat(extractSctMetadataFile(zip, dest)).isEqualTo(dest.resolve("project.sct"))
    }

    @Test
    fun `extractSctMetadataFile returns null and extracts nothing when an entry is rejected`() {
        val zip = folder.newFile("metadata-rejected.zip").toPath()
        ZipOutputStream(FileOutputStream(zip.toFile())).use { zos ->
            // ".." is rejected on every platform, also after the SQL metadata flattening to the last name segment.
            listOf("project.sct", "..").forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write("x".toByteArray())
                zos.closeEntry()
            }
        }
        val dest = folder.newFolder("sql")

        assertThat(extractSctMetadataFile(zip, dest)).isNull()
        assertThat(dest.listFiles()).isEmpty()
    }

    @Test
    fun `extractSctMetadataFile returns null for a file that is not a zip`() {
        // The SQL metadata file chooser accepts any file, and ZipFile throws an IOException for one that is not a zip.
        val notZip = folder.newFile("metadata.zip")
        notZip.writeText("this is not a zip archive")
        val dest = folder.newFolder("sql")

        assertThat(extractSctMetadataFile(notZip.toPath(), dest)).isNull()
        assertThat(dest.listFiles()).isEmpty()
    }
}
