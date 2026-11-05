// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.q.jetbrains.utils.normalizeFileUri
import java.net.URI

class WorkspaceFolderUtilTest {

    @Test
    fun `createWorkspaceFolders returns empty list when project is default`() {
        val mockProject = mockk<Project>()
        every { mockProject.isDefault } returns true

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result).isEmpty()
    }

    @Test
    fun `createWorkspaceFolders returns workspace folders for non-default project with modules`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val mockModule1 = mockk<Module>()
        val mockModule2 = mockk<Module>()
        val mockModuleRootManager1 = mockk<ModuleRootManager>()
        val mockModuleRootManager2 = mockk<ModuleRootManager>()

        val mockContentRoot1 = createMockVirtualFile(
            URI("file:///path/to/root1"),
            name = "root1"
        )
        val mockContentRoot2 = createMockVirtualFile(
            URI("file:///path/to/root2"),
            name = "root2"
        )

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(mockModule1, mockModule2)
        every { mockModule1.name } returns "module1"
        every { mockModule2.name } returns "module2"
        every { ModuleRootManager.getInstance(mockModule1) } returns mockModuleRootManager1
        every { ModuleRootManager.getInstance(mockModule2) } returns mockModuleRootManager2
        every { mockModuleRootManager1.contentRoots } returns arrayOf(mockContentRoot1)
        every { mockModuleRootManager2.contentRoots } returns arrayOf(mockContentRoot2)

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result).hasSize(2)
        assertThat(result[0].uri).isEqualTo(normalizeFileUri("file:///path/to/root1"))
        assertThat(result[1].uri).isEqualTo(normalizeFileUri("file:///path/to/root2"))
        assertThat(result[0].name).isEqualTo("module1")
        assertThat(result[1].name).isEqualTo("module2")
    }

    @Test
    fun `createWorkspaceFolders handles modules with no content roots`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val mockModule = mockk<Module>()
        val mockModuleRootManager = mockk<ModuleRootManager>()

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(mockModule)
        every { ModuleRootManager.getInstance(mockModule) } returns mockModuleRootManager
        every { mockModuleRootManager.contentRoots } returns emptyArray()

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result).isEmpty()
    }

    @Test
    fun `createWorkspaceFolders returns every content root of a multi-root module`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val mockModule = mockk<Module>()
        val mockModuleRootManager = mockk<ModuleRootManager>()

        val mockContentRoot1 = createMockVirtualFile(URI("file:///path/to/root1"), name = "root1")
        val mockContentRoot2 = createMockVirtualFile(URI("file:///path/to/root2"), name = "root2")
        val mockContentRoot3 = createMockVirtualFile(URI("file:///path/to/root3"), name = "root3")

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(mockModule)
        every { mockModule.name } returns "module1"
        every { ModuleRootManager.getInstance(mockModule) } returns mockModuleRootManager
        every { mockModuleRootManager.contentRoots } returns arrayOf(mockContentRoot1, mockContentRoot2, mockContentRoot3)

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result.map { it.uri }).containsExactly(
            normalizeFileUri("file:///path/to/root1"),
            normalizeFileUri("file:///path/to/root2"),
            normalizeFileUri("file:///path/to/root3")
        )
        // the server uses the folder name as a path segment, so names must stay unique
        assertThat(result.map { it.name }).containsExactly("module1/root1", "module1/root2", "module1/root3")
    }

    @Test
    fun `createWorkspaceFolders keeps the module name when a module has a single content root`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val multiRootModule = mockk<Module>()
        val singleRootModule = mockk<Module>()
        val multiRootManager = mockk<ModuleRootManager>()
        val singleRootManager = mockk<ModuleRootManager>()

        val attachedRoot1 = createMockVirtualFile(URI("file:///path/to/attached1"), name = "attached1")
        val attachedRoot2 = createMockVirtualFile(URI("file:///path/to/attached2"), name = "attached2")
        val soleRoot = createMockVirtualFile(URI("file:///path/to/sole"), name = "sole")

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(multiRootModule, singleRootModule)
        every { multiRootModule.name } returns "module1"
        every { singleRootModule.name } returns "module2"
        every { ModuleRootManager.getInstance(multiRootModule) } returns multiRootManager
        every { ModuleRootManager.getInstance(singleRootModule) } returns singleRootManager
        every { multiRootManager.contentRoots } returns arrayOf(attachedRoot1, attachedRoot2)
        every { singleRootManager.contentRoots } returns arrayOf(soleRoot)

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result.map { it.name }).containsExactly("module1/attached1", "module1/attached2", "module2")
        assertThat(result.map { it.uri }).containsExactly(
            normalizeFileUri("file:///path/to/attached1"),
            normalizeFileUri("file:///path/to/attached2"),
            normalizeFileUri("file:///path/to/sole")
        )
    }

    @Test
    fun `createWorkspaceFolders skips modules with no content roots but keeps the rest`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val emptyModule = mockk<Module>()
        val populatedModule = mockk<Module>()
        val emptyRootManager = mockk<ModuleRootManager>()
        val populatedRootManager = mockk<ModuleRootManager>()

        val root1 = createMockVirtualFile(URI("file:///path/to/root1"), name = "root1")
        val root2 = createMockVirtualFile(URI("file:///path/to/root2"), name = "root2")

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(emptyModule, populatedModule)
        every { emptyModule.name } returns "empty"
        every { populatedModule.name } returns "populated"
        every { ModuleRootManager.getInstance(emptyModule) } returns emptyRootManager
        every { ModuleRootManager.getInstance(populatedModule) } returns populatedRootManager
        every { emptyRootManager.contentRoots } returns emptyArray()
        every { populatedRootManager.contentRoots } returns arrayOf(root1, root2)

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result.map { it.name }).containsExactly("populated/root1", "populated/root2")
    }

    private fun createMockVirtualFile(uri: URI, name: String): VirtualFile =
        mockk<VirtualFile> {
            every { url } returns uri.toString()
            every { getName() } returns name
            every { isDirectory } returns false
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
        }
}
