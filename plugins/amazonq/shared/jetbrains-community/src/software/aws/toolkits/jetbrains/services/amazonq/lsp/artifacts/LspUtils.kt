// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.system.CpuArch
import org.apache.commons.io.FileUtils
import software.amazon.q.core.utils.ZIP_PROPERTY_POSIX
import software.amazon.q.core.utils.createParentDirectories
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.hasPosixFilePermissions
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

fun getToolkitsCommonCacheRoot(): Path = when {
    SystemInfo.isWindows -> {
        Paths.get(System.getenv("LOCALAPPDATA"))
    }
    SystemInfo.isMac -> {
        Paths.get(System.getProperty("user.home"), "Library", "Caches")
    }
    else -> {
        Paths.get(System.getProperty("user.home"), ".cache")
    }
}

fun getCurrentOS(): String = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isMac -> "darwin"
    else -> "linux"
}

fun getCurrentArchitecture() = when (CpuArch.CURRENT) {
    CpuArch.X86_64 -> "x64"
    CpuArch.ARM64 -> "arm64"
    else -> "unknown"
}

fun generateMD5Hash(filePath: Path): String {
    val messageDigest = DigestUtil.md5()
    DigestUtil.updateContentHash(messageDigest, filePath)
    return StringUtil.toHexString(messageDigest.digest())
}

fun generateSHA384Hash(filePath: Path): String {
    val messageDigest = MessageDigest.getInstance("SHA-384")
    DigestUtil.updateContentHash(messageDigest, filePath)
    return StringUtil.toHexString(messageDigest.digest())
}

fun getSubFolders(basePath: Path): List<Path> = try {
    basePath.listDirectoryEntries()
        .filter { it.isDirectory() }
} catch (e: Exception) {
    emptyList()
}

fun moveFilesFromSourceToDestination(sourceDir: Path, targetDir: Path) {
    try {
        Files.createDirectories(targetDir.parent)
        // NIO move does not work when copying across mount points (i.e. /tmp is on tmpfs)
        FileUtils.moveDirectory(sourceDir.toFile(), targetDir.toFile())
    } catch (e: Exception) {
        throw IllegalStateException("Failed to move files from $sourceDir to $targetDir", e)
    }
}

/**
 * Resolves the zip entry named [entryName] under [destDir] and verifies it does not escape that directory.
 *
 * `extractZipFile` builds each output path by joining the entry name onto the destination directory. A
 * remote archive — the Flare language-server zip is fetched from a CDN and extracted at IDE startup — can
 * carry an entry whose name contains `..` segments (or otherwise resolves outside the destination): a
 * zip-slip (CWE-22) that would let the archive write or overwrite files anywhere the IDE user can write.
 * The joined path is normalized and required to stay under the normalized destination; an entry that
 * escapes throws, which aborts the whole extraction rather than writing a single file outside [destDir].
 * A leading-slash entry name (how a top-level entry stringifies when walked from the zip root) still
 * resolves under [destDir], so legitimate archives are unaffected.
 */
internal fun resolveZipEntryUnderRoot(destDir: Path, entryName: String): Path {
    val root = destDir.toAbsolutePath().normalize()
    val destPath = Paths.get(root.toString(), entryName).normalize()
    if (!destPath.startsWith(root)) {
        throw LspException(
            "Zip entry '$entryName' resolves outside the extraction directory",
            LspException.ErrorCode.UNZIP_FAILED,
        )
    }
    return destPath
}

fun extractZipFile(zipFilePath: Path, destDir: Path) {
    if (!zipFilePath.exists()) {
        throw FileNotFoundException("Zip file not found: $zipFilePath")
    }

    try {
        FileSystems.newFileSystem(
            // jar prefix due to potentially ambiguous resolution to wrong fs impl for zipfs on windows
            URI("jar:${zipFilePath.toUri()}"),
            mapOf(ZIP_PROPERTY_POSIX to destDir.hasPosixFilePermissions())
        ).use { zipfs ->
            Files.walk(zipfs.getPath("/")).use { paths ->
                paths
                    .filter { !it.isDirectory() }
                    .forEach { zipEntry ->
                        val destPath = resolveZipEntryUnderRoot(destDir, zipEntry.toString())
                        destPath.createParentDirectories()
                        Files.copy(zipEntry, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                    }
            }
        }
    } catch (e: LspException) {
        throw e
    } catch (e: Exception) {
        throw LspException("Failed to extract zip file: ${e.message}", LspException.ErrorCode.UNZIP_FAILED, cause = e)
    }
}
