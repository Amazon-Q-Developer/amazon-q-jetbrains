// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import kotlinx.coroutines.withContext
import software.amazon.q.core.utils.createParentDirectories
import software.amazon.q.core.utils.createTemporaryZipFile
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.putNextEntry
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager.Companion.LOG
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_ARTIFACT_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_ARTIFACT_POMFOLDER_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCY_REPORT_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCY_REPORT_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCY_ROOT_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_POM_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_POM_VERSION_PLACEHOLDER
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_UPLOAD_ZIP_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.controller.CodeTransformChatController
import software.aws.toolkits.jetbrains.services.codemodernizer.model.AURORA_DB
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerArtifact.Companion.XML_MAPPER
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DependencyUpdatesReport
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_CONFIGURATION_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ORACLE_DB
import software.aws.toolkits.jetbrains.services.codemodernizer.model.RDS_DB
import software.aws.toolkits.jetbrains.services.codemodernizer.model.SctMetadata
import software.aws.toolkits.jetbrains.services.codemodernizer.model.SqlMetadataValidationResult
import software.aws.toolkits.jetbrains.services.codewhisperer.util.content
import software.aws.toolkits.resources.message
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path

// Zip entry names separate folders with '/' on every platform; some Windows archivers write '\' instead.
private const val ZIP_ENTRY_SEPARATOR = '/'
private const val BACKSLASH_ENTRY_SEPARATOR = '\\'

fun filterOnlyParentFiles(filePaths: Set<VirtualFile>): List<VirtualFile> {
    if (filePaths.isEmpty()) return emptyList()
    // sorts it like:
    // foo
    // foo/bar
    // foo/bar/bas
    val sorted = filePaths.sortedBy { Path(it.path).nameCount }
    val uniquePrefixes = mutableSetOf(Path(sorted.first().path).parent)
    val shortestRoots = mutableSetOf(sorted.first())
    shortestRoots.add(sorted.first())
    sorted.drop(1).forEach { file ->
        if (uniquePrefixes.none { Path(file.path).startsWith(it) }) {
            shortestRoots.add(file)
            uniquePrefixes.add(Path(file.path).parent)
        } else if (Path(file.path).parent in uniquePrefixes) {
            shortestRoots.add(file) // handles multiple parent files on the same level
        }
    }
    return shortestRoots.toList()
}

fun createClientSideBuildUploadZip(exitCode: Int?, stdout: String): File {
    val mapper = jacksonObjectMapper()
    val outputFile = createTemporaryZipFile { zip ->
        val manifest = mapOf(
            "capability" to "CLIENT_SIDE_BUILD",
            "exitCode" to exitCode,
            "commandLogFileName" to "build-output.log"
        )
        mapper.writeValueAsString(manifest)
            .byteInputStream()
            .use { inputStream ->
                zip.putNextEntry(Path("manifest.json").toString(), inputStream)
            }

        stdout.byteInputStream().use { inputStream ->
            zip.putNextEntry(Path("build-output.log").toString(), inputStream)
        }
    }
    return outputFile.toFile()
}

/**
 * @description For every directory, check if any supported build files (pom.xml etc) exists.
 * If we find a valid build file, store it and stop further recursion.
 */
fun findBuildFiles(sourceFolder: File, supportedBuildFileNames: List<String>): List<File> {
    val buildFiles = mutableListOf<File>()
    sourceFolder.walkTopDown()
        .maxDepth(5)
        .onEnter { currentDir ->
            supportedBuildFileNames.forEach {
                val maybeSupportedFile = currentDir.resolve(MAVEN_CONFIGURATION_FILE_NAME)
                if (maybeSupportedFile.exists()) {
                    buildFiles.add(maybeSupportedFile)
                    return@onEnter false
                }
            }
            return@onEnter true
        }.forEach {
            // noop, collects the sequence
        }
    return buildFiles
}

fun parseBuildFile(buildFile: VirtualFile?): String? {
    val absolutePaths = mutableListOf("users/", "system/", "volumes/", "c:\\", "d:\\")
    val alias = System.getProperty("user.home").substringAfterLast(File.separator)
    absolutePaths.add(alias)
    if (buildFile != null && buildFile.exists()) {
        val buildFileContents = buildFile.content().lowercase()
        val detectedPaths: MutableList<String> = mutableListOf()
        for (path in absolutePaths) {
            if (buildFileContents.contains(path)) {
                detectedPaths.add(path)
            }
        }
        if (detectedPaths.size > 0) {
            val warningMessage =
                message(
                    "codemodernizer.chat.message.absolute_path_detected",
                    detectedPaths.size,
                    buildFile.name,
                    detectedPaths.joinToString(", "),
                )
            LOG.info { "CodeTransformation: absolute path potentially in build file" }
            return warningMessage
        }
    }
    return null
}

/**
 * Resolves [entryName] under [destDir] and verifies it does not escape that directory. unzipFile extracts
 * archives downloaded from the transformation service with java.util.zip.ZipFile, which does not reject
 * parent-relative or absolute entry names, so a `..` (or absolute) entry would otherwise write outside
 * [destDir] and could overwrite files the IDE can write. The resolved path is normalized and required to
 * stay under the normalized destination; an entry that escapes throws and aborts the extraction.
 */
internal fun resolveZipEntryUnderRoot(destDir: Path, entryName: String): Path {
    val root = destDir.toAbsolutePath().normalize()
    // entryName comes from the archive; strip control characters and line separators and truncate it before it
    // can reach logs or telemetry through an exception message.
    val safeName = entryName.take(100).filter { !it.isISOControl() && it != '\u2028' && it != '\u2029' }
    val destPath = try {
        root.resolve(entryName).normalize()
    } catch (e: InvalidPathException) {
        throw IllegalArgumentException("Code Transform zip entry has an invalid name: $safeName")
    }
    require(destPath != root && destPath.startsWith(root)) {
        "Code Transform zip entry resolves outside the extraction directory: $safeName"
    }
    return destPath
}

/**
 * Unzips a zip into a dir. Returns the true when successfully unzips the file pointed to by [zipFilePath] to [destDir]
 *
 * @throws IllegalArgumentException if an entry has an invalid name or resolves outside [destDir]. Every entry is
 * checked before any is written, so nothing is extracted in that case.
 */
fun unzipFile(zipFilePath: Path, destDir: Path, isSqlMetadata: Boolean = false, extractOnlySources: Boolean = false): Boolean {
    if (!zipFilePath.exists()) return false
    val zipFile = ZipFile(zipFilePath.toFile())
    zipFile.use { file ->
        val entriesToExtract = file.entries().asSequence()
            .filterNot { it.isDirectory }
            .mapNotNull { zipEntry ->
                var fileName = zipEntry.name
                if (extractOnlySources) {
                    // used to copy just the source code for client-side build
                    if (!fileName.startsWith("sources")) {
                        return@mapNotNull null
                    }
                }
                if (isSqlMetadata) {
                    // when manually compressing ZIP files, the files get unzipped under a subdirectory where we extract them to,
                    // this change puts the files directly under the root of the target directory, which is what we want.
                    // Flatten on both entry separators, not on File.separatorChar, so the result is the same on every platform.
                    fileName = zipEntry.name.substringAfterLast(ZIP_ENTRY_SEPARATOR).substringAfterLast(BACKSLASH_ENTRY_SEPARATOR)
                    if (fileName.isEmpty()) {
                        // a folder entry written with '\', which ZipEntry.isDirectory does not recognize
                        return@mapNotNull null
                    }
                }
                zipEntry to resolveZipEntryUnderRoot(destDir, fileName)
            }.toList()
        entriesToExtract.forEach { (zipEntry, destPath) ->
            destPath.createParentDirectories()
            FileOutputStream(destPath.toFile()).use { targetFile ->
                file.getInputStream(zipEntry).copyTo(targetFile)
            }
        }
    }
    return true
}

/**
 * Extracts the SQL metadata zip at [zipPath] into [destDir] and returns its `.sct` file. Returns null when the zip has
 * no `.sct` file, when [unzipFile] rejects one of its entries (nothing is extracted in that case), or when the file
 * cannot be read as a zip.
 */
fun extractSctMetadataFile(zipPath: Path, destDir: File): File? {
    try {
        unzipFile(zipPath, destDir.toPath(), isSqlMetadata = true)
    } catch (e: IllegalArgumentException) {
        getLogger<CodeTransformChatController>().warn { "Could not extract the SQL metadata zip: ${e.message}" }
        return null
    } catch (e: IOException) {
        // The user can select any file, for example one that is not a zip or a truncated zip. The exception message
        // can contain a path built from an entry name, so log only the exception type.
        getLogger<CodeTransformChatController>().warn { "Could not read the SQL metadata zip: ${e.javaClass.name}" }
        return null
    }
    return destDir.listFiles { file -> file.name.endsWith(".sct") }?.firstOrNull()
}

suspend fun zipToPath(byteArrayList: List<ByteArray>, outputDirPath: Path? = null): Pair<Path, Int> {
    val zipFilePath = withContext(getCoroutineBgContext()) {
        if (outputDirPath == null) {
            Files.createTempFile(null, ".zip")
        } else {
            Files.createTempFile(outputDirPath, null, ".zip")
        }
    }
    var totalDownloadBytes = 0
    withContext(getCoroutineBgContext()) {
        Files.newOutputStream(zipFilePath).use {
            for (bytes in byteArrayList) {
                it.write(bytes)
                totalDownloadBytes += bytes.size
            }
        }
    }
    return zipFilePath to totalDownloadBytes
}

fun parseXmlDependenciesReport(pathToXmlDependency: Path): DependencyUpdatesReport {
    val reportFile = pathToXmlDependency.toFile()
    val report = XML_MAPPER.readValue(reportFile, DependencyUpdatesReport::class.java)
    return report
}

// return the first missing key in the custom versions file, or null if all required keys are present
fun validateCustomVersionsFile(file: VirtualFile): String? {
    val validFileEndings = listOf("yaml", "yml")
    if (!validFileEndings.any { file.name.lowercase().endsWith(it) }) {
        getLogger<CodeTransformChatController>().error { "Custom versions file is not a YAML file: ${file.name}" }
        return message("codemodernizer.chat.message.custom_dependency_upgrades_invalid_not_yaml")
    }
    val fileContents = file.readText()
    val requiredKeys = listOf("dependencyManagement", "identifier", "targetVersion", "originType")
    for (key in requiredKeys) {
        if (!fileContents.contains(key)) {
            getLogger<CodeTransformChatController>().error { "Missing yaml key: $key" }
            return key
        }
    }
    return null
}

fun validateSctMetadata(sctFile: File?): SqlMetadataValidationResult {
    if (sctFile == null) {
        return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.missing_sct_file"))
    }

    val sctMetadata: SctMetadata
    try {
        sctMetadata = XML_MAPPER.readValue<SctMetadata>(sctFile)
    } catch (e: Exception) {
        getLogger<CodeTransformChatController>().error { "Error parsing .sct metadata file; invalid XML encountered: ${e.localizedMessage}" }
        return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.invalid_sct"))
    }

    try {
        val projectModel = sctMetadata.instances.projectModel
        val sourceDbServer = projectModel.entities.sources.dbServer
        val sourceVendor = sourceDbServer.vendor.trim().uppercase()
        if (sourceVendor != ORACLE_DB) {
            return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.invalid_source_db"))
        }

        val sourceServerName = sourceDbServer.name.trim()

        val targetDbServer = projectModel.entities.targets.dbServer
        val targetVendor = targetDbServer.vendor.trim().uppercase()
        if (targetVendor != AURORA_DB && targetVendor != RDS_DB) {
            return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.invalid_target_db"))
        }

        val schemaNames = mutableSetOf<String>()
        projectModel.relations.serverNodeLocation.forEach { serverNodeLocation ->
            val fullNameNodeInfoList = serverNodeLocation.fullNameNodeInfoList.nameParts.fullNameNodeInfo
            fullNameNodeInfoList.forEach { node ->
                if (node.typeNode.lowercase() == "schema") {
                    schemaNames.add(node.nameNode.uppercase())
                }
            }
        }
        return SqlMetadataValidationResult(true, "", sourceVendor, targetVendor, sourceServerName, schemaNames)
    } catch (e: Exception) {
        getLogger<CodeTransformChatController>().error { "Error parsing .sct metadata file: ${e.localizedMessage}" }
        return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.invalid_sct"))
    }
}

fun createFileCopy(originalFile: File, outputPath: Path): File {
    val outputFile = outputPath.toFile()

    originalFile.inputStream().use { inputStream ->
        FileUtil.createParentDirs(outputFile)

        FileOutputStream(outputFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    return outputFile
}

fun setDependencyVersionInPom(pomFile: File, version: String) {
    val existingValue = pomFile.readText()
    val newValue = existingValue.replace(HIL_POM_VERSION_PLACEHOLDER, version)
    pomFile.writeText(newValue)
}

fun getPathToHilArtifactDir(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_ARTIFACT_DIR_NAME)

fun getPathToHilArtifactPomFolder(tmpDirPath: Path): Path = getPathToHilArtifactDir(tmpDirPath).resolve(HIL_ARTIFACT_POMFOLDER_DIR_NAME)

fun getPathToHilArtifactPomFile(tmpDirPath: Path): Path = getPathToHilArtifactPomFolder(tmpDirPath).resolve(HIL_POM_FILE_NAME)

fun getPathToHilDependencyReportDir(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_DEPENDENCY_REPORT_DIR_NAME)

fun getPathToHilDependencyReport(tmpDirPath: Path): Path = getPathToHilDependencyReportDir(tmpDirPath).resolve("target/$HIL_DEPENDENCY_REPORT_FILE_NAME")

fun getPathToHilDependenciesRootDir(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_DEPENDENCY_ROOT_DIR_NAME)

fun getPathToHilUploadZip(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_UPLOAD_ZIP_NAME)

fun findLineNumberByString(virtualFile: VirtualFile, searchString: String): Int? {
    val text = runReadAction {
        FileDocumentManager.getInstance().getDocument(virtualFile)?.text
    } ?: return null

    val lines = text.split("\n")

    for ((lineNumber, line) in lines.withIndex()) {
        if (line.contains(searchString)) {
            return lineNumber
        }
    }

    return null
}
