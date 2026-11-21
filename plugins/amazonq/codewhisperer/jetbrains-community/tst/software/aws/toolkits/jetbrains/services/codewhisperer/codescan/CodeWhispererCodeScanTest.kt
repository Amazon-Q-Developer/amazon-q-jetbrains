// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.test.runTest
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyString
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.q.core.utils.WaiterTimeoutException
import software.amazon.q.jetbrains.utils.isInstanceOf
import software.amazon.q.jetbrains.utils.isInstanceOfSatisfying
import software.amazon.q.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.Payload
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.applySuggestedFix
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getTelemetryErrorMessage
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import kotlin.io.path.relativeTo
import kotlin.test.assertNotNull
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.SuggestedFix as ScanSuggestedFix

class CodeWhispererCodeScanTest : CodeWhispererCodeScanTestBase(PythonCodeInsightTestFixtureRule()) {
    @Rule
    @JvmField
    val outsideFolder = TemporaryFolder()

    private lateinit var psifile: PsiFile
    private lateinit var file: File
    private lateinit var sessionConfigSpy: CodeScanSessionConfig
    private val payloadContext = PayloadContext(CodewhispererLanguage.Python, 1, 1, 10, listOf(), 600, 200)
    private lateinit var codeScanSessionContext: CodeScanSessionContext
    private lateinit var codeScanSessionSpy: CodeWhispererCodeScanSession
    private val codeScanName = UUID.randomUUID().toString()

    @Before
    override fun setup() {
        super.setup()
        psifile = projectRule.fixture.addFileToProject(
            "/test.py",
            """import numpy as np
               import from module1 import helper
               
               def add(a, b):
                  return a + b
                  
            """.trimMargin()
        )
        file = psifile.virtualFile.toNioPath().toFile()

        sessionConfigSpy = spy(
            CodeScanSessionConfig.create(
                psifile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.PROJECT,
                false
            )
        )
        setupResponse(psifile.virtualFile.toNioPath().relativeTo(sessionConfigSpy.projectRoot.toNioPath()))

        sessionConfigSpy.stub {
            onGeneric { sessionConfigSpy.createPayload() }.thenReturn(Payload(payloadContext, file))
        }

        // Mock CodeWhispererClient needs to be setup before initializing CodeWhispererCodeScanSession
        codeScanSessionContext = CodeScanSessionContext(project, sessionConfigSpy, CodeWhispererConstants.CodeAnalysisScope.PROJECT)
        codeScanSessionSpy = spy(CodeWhispererCodeScanSession(codeScanSessionContext))

        mockClient.stub {
            onGeneric { createUploadUrl(any()) }.thenReturn(fakeCreateUploadUrlResponse)
            onGeneric { createCodeScan(any()) }.thenReturn(fakeCreateCodeScanResponse)
            onGeneric { getCodeScan(any()) }.thenReturn(fakeGetCodeScanResponse)
            onGeneric { listCodeScanFindings(any()) }.thenReturn(fakeListCodeScanFindingsResponse)
        }
    }

    @Test
    fun `test createUploadUrlAndUpload()`() {
        val fileMd5: String = Base64.getEncoder().encodeToString(DigestUtils.md5(FileInputStream(file)))
        zipUploadManagerSpy.stub {
            onGeneric { zipUploadManagerSpy.createUploadUrl(any(), any(), any(), any(), any()) }
                .thenReturn(fakeCreateUploadUrlResponse)
        }

        zipUploadManagerSpy.createUploadUrlAndUpload(
            file,
            "artifactType",
            CodeWhispererConstants.UploadTaskType.SCAN_FILE,
            codeScanName,
            CodeWhispererConstants.FeatureName.CODE_REVIEW
        )

        val inOrder = inOrder(zipUploadManagerSpy)
        inOrder.verify(zipUploadManagerSpy).createUploadUrl(eq(fileMd5), eq("artifactType"), any(), any(), any())
        inOrder.verify(zipUploadManagerSpy).uploadArtifactToS3(
            eq(fakeCreateUploadUrlResponse.uploadUrl()),
            eq(fakeCreateUploadUrlResponse.uploadId()),
            eq(file),
            eq(fileMd5),
            eq(null),
            any(),
            any()
        )
    }

    @Test
    fun `test createUploadUrlAndUpload() with invalid source zip file`() {
        val invalidZipFile = File("/path/file.zip")

        assertThrows<CodeWhispererCodeScanException> {
            zipUploadManagerSpy.createUploadUrlAndUpload(
                invalidZipFile,
                "artifactType",
                CodeWhispererConstants.UploadTaskType.SCAN_FILE,
                codeScanName,
                CodeWhispererConstants.FeatureName.CODE_REVIEW
            )
        }
    }

    @Test
    fun `test createUploadUrl()`() {
        val response = zipUploadManagerSpy.createUploadUrl(
            "md5",
            "type",
            CodeWhispererConstants.UploadTaskType.SCAN_FILE,
            codeScanName,
            featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW
        )

        argumentCaptor<CreateUploadUrlRequest>().apply {
            verify(mockClient).createUploadUrl(capture())
            assertThat(response.uploadUrl()).isEqualTo(s3endpoint)
            assertThat(response.uploadId()).isEqualTo(UPLOAD_ID)
            assertThat(firstValue.contentMd5()).isEqualTo("md5")
            assertThat(firstValue.artifactTypeAsString()).isEqualTo("type")
        }
    }

    @Test
    fun `test mapToCodeScanIssues`() {
        val recommendations = listOf(
            fakeListCodeScanFindingsResponse.codeAnalysisFindings(),
            getFakeRecommendationsOnNonExistentFile()
        )
        val res = codeScanSessionSpy.mapToCodeScanIssues(recommendations, project, "jobId")
        assertThat(res).hasSize(2)
    }

    @Test
    fun `test mapToCodeScanIssues - handles index out of bounds`() {
        val recommendations = listOf(
            fakeListCodeScanFindingsOutOfBoundsIndexResponse.codeAnalysisFindings(),
        )
        val res = codeScanSessionSpy.mapToCodeScanIssues(recommendations, project, "jobId")
        assertThat(res).hasSize(1)
    }

    @Test
    fun `mapToCodeScanIssues drops a finding whose path leaves the project`() {
        val outside = outsideFile().toNioPath()
        val outsidePath = sessionConfigSpy.projectRoot.toNioPath().relativize(outside).toString().replace('\\', '/')

        val res = codeScanSessionSpy.mapToCodeScanIssues(listOf(findingAt(outsidePath)), project, "jobId")

        assertThat(res).isEmpty()
    }

    @Test
    fun `mapToCodeScanIssues drops a finding whose file links outside the project`() {
        val link = sessionConfigSpy.projectRoot.toNioPath().resolve("linked.py")
        createSymbolicLinkOrSkip(link, outsideFile().toNioPath())
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(link))

        val res = codeScanSessionSpy.mapToCodeScanIssues(listOf(findingAt("linked.py")), project, "jobId")

        assertThat(res).isEmpty()
    }

    @Test
    fun `mapToCodeScanIssues drops a finding outside the project content roots in a project review`() {
        val outside = outsideFile()
        // Root the review at the file's folder, so only the content-root check can drop the finding.
        sessionConfigSpy.stub { onGeneric { sessionConfigSpy.projectRoot }.thenReturn(checkNotNull(outside.parent)) }

        val res = codeScanSessionSpy.mapToCodeScanIssues(listOf(findingAt(outside.name)), project, "jobId")

        assertThat(res).isEmpty()
    }

    @Test
    fun `mapToCodeScanIssues keeps a finding on the reviewed file of a file review outside the project`() {
        val reviewed = outsideFile()

        // Automatic file reviews and file reviews started from chat map findings the same way.
        for (initiatedByChat in listOf(false, true)) {
            val res = fileReviewOf(reviewed, initiatedByChat)
                .mapToCodeScanIssues(listOf(findingAt("${project.name}/${reviewed.name}")), project, "jobId")

            assertThat(res).hasSize(1)
            assertThat(res.single().file).isEqualTo(reviewed)
            assertThat(res.single().isReviewedFileOutsideProject).isTrue()
        }
    }

    @Test
    fun `mapToCodeScanIssues keeps the project check for an outside selected file in project and agentic reviews`() {
        val outside = outsideFile()

        for (scope in listOf(CodeWhispererConstants.CodeAnalysisScope.PROJECT, CodeWhispererConstants.CodeAnalysisScope.AGENTIC)) {
            val config = spy(CodeScanSessionConfig.create(outside, project, scope, false))
            // Root the review at the file's folder, so only the review scope decides whether the finding is kept.
            config.stub { onGeneric { config.projectRoot }.thenReturn(checkNotNull(outside.parent)) }
            val session = CodeWhispererCodeScanSession(CodeScanSessionContext(project, config, scope))

            val res = session.mapToCodeScanIssues(listOf(findingAt("${project.name}/${outside.name}")), project, "jobId")

            assertThat(res).isEmpty()
        }
    }

    @Test
    fun `mapToCodeScanIssues drops a finding on a reviewed link inside the project that points outside it`() {
        // Next to test.py, so the link itself is inside a content root while the file it points to is not.
        val link = psifile.virtualFile.parent.toNioPath().resolve("reviewed-link.py")
        createSymbolicLinkOrSkip(link, outsideFile().toNioPath())
        val linkFile = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(link))
        val review = fileReviewOf(linkFile)
        val findingPath = link.relativeTo(review.sessionContext.sessionConfig.projectRoot.toNioPath()).toString().replace('\\', '/')

        val res = review.mapToCodeScanIssues(listOf(findingAt(findingPath)), project, "jobId")

        assertThat(res).isEmpty()
    }

    @Test
    fun `mapToCodeScanIssues drops a finding on another file outside the project during a file review`() {
        val reviewed = outsideFile()
        val sibling = outsideFile(name = "sibling.py")

        val res = fileReviewOf(reviewed).mapToCodeScanIssues(listOf(findingAt("${project.name}/${sibling.name}")), project, "jobId")

        assertThat(res).isEmpty()
    }

    @Test
    fun `mapToCodeScanIssues keeps the project check for the reviewed file of a file review inside the project`() {
        val review = fileReviewOf(psifile.virtualFile)
        val root = review.sessionContext.sessionConfig.projectRoot.toNioPath()
        val findingPath = psifile.virtualFile.toNioPath().relativeTo(root).toString().replace('\\', '/')

        val res = review.mapToCodeScanIssues(listOf(findingAt(findingPath)), project, "jobId")

        assertThat(res).hasSize(1)
        assertThat(res.single().isReviewedFileOutsideProject).isFalse()
    }

    @Test
    fun `isIssueFileInsideProject accepts a project file and rejects a file outside the project`() {
        assertThat(isIssueFileInsideProject(project, psifile.virtualFile)).isTrue()
        assertThat(isIssueFileInsideProject(project, outsideFile())).isFalse()
    }

    @Test
    fun `isIssueFileInsideProject compares real paths instead of link paths`() {
        val projectDir = psifile.virtualFile.parent.toNioPath()
        val linkToOutside = projectDir.resolve("link-to-outside.py")
        val linkToProject = outsideFolder.root.toPath().resolve("link-to-project")
        createSymbolicLinkOrSkip(linkToOutside, outsideFile().toNioPath())
        createSymbolicLinkOrSkip(linkToProject, projectDir)
        val fileSystem = LocalFileSystem.getInstance()

        // The link is in the project, but the file it points to is not.
        assertThat(isIssueFileInsideProject(project, checkNotNull(fileSystem.refreshAndFindFileByNioFile(linkToOutside)))).isFalse()
        // The link is outside the project, but the file reached through it is in the project.
        assertThat(isIssueFileInsideProject(project, checkNotNull(fileSystem.refreshAndFindFileByNioFile(linkToProject.resolve("test.py"))))).isTrue()
    }

    @Test
    fun `applySuggestedFix does not change a file outside the project`() {
        val content = "first line\nsecond line\nforth line"
        val fix = ScanSuggestedFix("fix", "@@ -1,3 +1,3 @@\n first line\n-second line\n+third line\n forth line")
        val issue = issueFor(outsideFile(content), listOf(fix))

        runInEdtAndWait { applySuggestedFix(project, issue) }

        assertThat(runReadAction { issue.document.text }).isEqualTo(content)
    }

    @Test
    fun `ignoreSingleIssue does not change a file outside the project`() {
        val content = "first line\nsecond line\n"
        val issue = issueFor(outsideFile(content))

        scanManagerSpy.ignoreSingleIssue(issue)
        runInEdtAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

        assertThat(runReadAction { issue.document.text }).isEqualTo(content)
    }

    @Test
    fun `runCodeFixWorkflow does not upload a file outside the project`() = runTest {
        val response = AmazonQCodeFixSession(project).runCodeFixWorkflow(issueFor(outsideFile()))

        assertThat(response.getCodeFixJobResponse).isNull()
        assertThat(response.failureResponse).isEqualTo("The code fix targets a file outside the project.")
    }

    @Test
    fun `applySuggestedFix changes the reviewed file of a file review outside the project`() {
        val issue = reviewedFileIssue(outsideFile("first line\nsecond line\nforth line"))
        issue.suggestedFixes = listOf(ScanSuggestedFix("fix", "@@ -1,3 +1,3 @@\n first line\n-second line\n+third line\n forth line"))

        runInEdtAndWait { applySuggestedFix(project, issue) }

        assertThat(runReadAction { issue.document.text }).isEqualTo("first line\nthird line\nforth line")
    }

    @Test
    fun `ignoreSingleIssue adds the ignore comment to the reviewed file of a file review outside the project`() = runTest {
        val issue = reviewedFileIssue(outsideFile("first line\nsecond line\n"))
        // A scan shows its issues in the results view before Ignore can be used, so show this one there first.
        scanManagerSpy.testRenderResponseOnUIThread(listOf(issue), listOf(issue.file))

        runInEdtAndWait {
            scanManagerSpy.ignoreSingleIssue(issue)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }

        // The comment prefix comes from the language's commenter, so check the inserted line by its marker.
        val text = runReadAction { issue.document.text }
        assertThat(text.lines().first()).startsWith("#").contains(CodeWhispererConstants.amazonqIgnoreNextLine)
        assertThat(text.substringAfter('\n')).isEqualTo("first line\nsecond line\n")
    }

    @Test
    fun `runCodeFixWorkflow runs for the reviewed file of a file review outside the project`() = runTest {
        mockClient.stub {
            onGeneric { startCodeFixJob(any()) }.thenReturn(fakeStartCodeFixJobResponse)
            onGeneric { getCodeFixJob(any()) }.thenReturn(fakeGetCodeFixJobResponse)
        }

        val response = AmazonQCodeFixSession(project).runCodeFixWorkflow(reviewedFileIssue(outsideFile()))

        assertThat(response.failureResponse).isNull()
        assertThat(response.getCodeFixJobResponse).isEqualTo(fakeGetCodeFixJobResponse)
    }

    private fun outsideFile(content: String = "print('outside')\n", name: String = "outside.py"): VirtualFile {
        val path = outsideFolder.newFile(name).toPath()
        Files.writeString(path, content)
        return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
    }

    // createPayload sets the review root: the project root, or the file's folder for a file outside the project.
    private fun fileReviewOf(file: VirtualFile, initiatedByChat: Boolean = false): CodeWhispererCodeScanSession {
        val config = CodeScanSessionConfig.create(file, project, CodeWhispererConstants.CodeAnalysisScope.FILE, initiatedByChat)
        config.createPayload().srcZip.delete()
        return CodeWhispererCodeScanSession(CodeScanSessionContext(project, config, CodeWhispererConstants.CodeAnalysisScope.FILE))
    }

    private fun reviewedFileIssue(file: VirtualFile): CodeWhispererCodeScanIssue =
        fileReviewOf(file).mapToCodeScanIssues(listOf(findingAt("${project.name}/${file.name}")), project, "jobId").single()

    private fun createSymbolicLinkOrSkip(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: Exception) {
            Assume.assumeNoException("symlinks are not supported on this platform", e)
        }
    }

    private fun findingAt(filePath: String) = """
        [
            {
                "filePath": "$filePath",
                "startLine": 1,
                "endLine": 1,
                "title": "test",
                "description": {
                    "text": "global variable",
                    "markdown": "### global variable"
                },
                "detectorId": "detectorId",
                "detectorName": "detectorName",
                "findingId": "findingId",
                "relatedVulnerabilities": [],
                "codeSnippet": [],
                "severity": "Low",
                "remediation": {
                    "recommendation": {
                        "text": "recommendationText",
                        "url": "recommendationUrl"
                    },
                    "suggestedFixes": []
                }
            }
        ]
    """.trimIndent()

    private fun issueFor(file: VirtualFile, suggestedFixes: List<ScanSuggestedFix> = emptyList()) = CodeWhispererCodeScanIssue(
        project = project,
        file = file,
        startLine = 1,
        startCol = 1,
        endLine = 1,
        endCol = 1,
        title = "test",
        description = Description("global variable", "### global variable"),
        detectorId = "detectorId",
        detectorName = "detectorName",
        findingId = "findingId",
        ruleId = null,
        relatedVulnerabilities = emptyList(),
        severity = "Low",
        recommendation = Recommendation("recommendationText", "recommendationUrl"),
        suggestedFixes = suggestedFixes,
        codeSnippet = emptyList(),
        scanJobId = "jobId",
    )

    @Test
    fun `test getTelemetryErrorMessage should return the correct error message`() {
        val exceptions = listOf(
            Exception("Resource not found."),
            Exception("Service returned HTTP status code 407"),
            Exception("Service returned HTTP status code 403"),
            Exception("invalid_grant: Invalid token provided"),
            Exception("Connect timed out"),
            Exception("Encountered an unexpected error when processing the request, please try again."),
            Exception("Some other error message"),
            Exception("Improperly formed request")
        )

        val expectedMessages = listOf(
            "Resource not found.",
            "Service returned HTTP status code 407",
            "Service returned HTTP status code 403",
            "invalid_grant: Invalid token provided",
            "Unable to execute HTTP request: Connect timed out",
            "Encountered an unexpected error when processing the request, please try again.",
            "Some other error message",
            "Improperly formed request"
        )

        exceptions.forEachIndexed { index, exception ->
            val actualMessage = getTelemetryErrorMessage(exception, featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW)
            assertThat(expectedMessages[index]).isEqualTo(actualMessage)
        }
    }

    @Test
    fun `test run() - happypath`() = runTest {
        assertNotNull(sessionConfigSpy)
        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOfSatisfying<CodeScanResponse.Success> {
            assertThat(it.issues).hasSize(2)
            assertThat(it.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat(it.responseContext.codeScanJobId).isEqualTo("jobId")
        }

        verify(zipUploadManagerSpy, times(1)).createUploadUrlAndUpload(eq(file), eq("SourceCode"), any(), anyString(), any())
        val inOrder = inOrder(codeScanSessionSpy)
        inOrder.verify(codeScanSessionSpy, Times(1)).createCodeScan(eq(CodewhispererLanguage.Python.toString()), anyString())
        inOrder.verify(codeScanSessionSpy, Times(1)).getCodeScan(any())
        inOrder.verify(codeScanSessionSpy, Times(1)).listCodeScanFindings(eq("jobId"), eq(null))
    }

    @Test
    fun `test run() - code scans limit reached`() = runTest {
        assertNotNull(sessionConfigSpy)

        mockClient.stub {
            onGeneric { zipUploadManagerSpy.createUploadUrlAndUpload(any(), any(), any(), any(), any()) }.thenThrow(
                CodeWhispererRuntimeException.builder()
                    .message("Project Review Monthly Exceeded")
                    .requestId("abc123")
                    .statusCode(400)
                    .cause(RuntimeException("Something went wrong"))
                    .writableStackTrace(true)
                    .awsErrorDetails(
                        AwsErrorDetails.builder()
                            .errorCode("ThrottlingException")
                            .errorMessage("Maximum full project scan count reached for this month")
                            .serviceName("CodeWhispererService")
                            .build()
                    )
                    .build()
            )
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        if (codeScanResponse is CodeScanResponse.Failure) {
            assertThat(codeScanResponse.failureReason).isInstanceOf<CodeWhispererRuntimeException>()
            assertThat(codeScanResponse.failureReason.toString()).contains("Project Review Monthly Exceeded")
            assertThat(codeScanResponse.failureReason.cause.toString()).contains("java.lang.RuntimeException: Something went wrong")
        }
    }

    @Test
    fun `test run() - createCodeScan failed`() = runTest {
        mockClient.stub {
            onGeneric { createCodeScan(any()) }.thenReturn(fakeCreateCodeScanResponseFailed)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
    }

    @Test
    fun `test run() - createCodeScan error`() = runTest {
        mockClient.stub {
            onGeneric { createCodeScan(any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    @Test
    fun `test run() - getCodeScan failed`() = runTest {
        mockClient.stub {
            onGeneric { getCodeScan(any()) }.thenReturn(fakeGetCodeScanResponseFailed)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
    }

    @Test
    fun `test run() - getCodeScan pending timeout`() = runTest {
        sessionConfigSpy.stub {
            onGeneric { overallJobTimeoutInSeconds() }.thenReturn(5)
        }
        mockClient.stub {
            onGeneric { getCodeScan(any()) }.thenAnswer {
                Thread.sleep(TIMEOUT)
                fakeGetCodeScanResponsePending
            }
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<WaiterTimeoutException>()
    }

    @Test
    fun `test run() - getCodeScan error`() = runTest {
        mockClient.stub {
            onGeneric { getCodeScan(any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    @Test
    fun `test run() - listCodeScanFindings error`() = runTest {
        mockClient.stub {
            onGeneric { listCodeScanFindings(any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    companion object {
        const val TIMEOUT = 10L * TOTAL_MILLIS_IN_SECOND
    }
}
