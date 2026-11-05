// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.WorkspaceFolder
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString

object WorkspaceFolderUtil {
    fun createWorkspaceFolders(project: Project): List<WorkspaceFolder> =
        if (project.isDefault) {
            emptyList()
        } else {
            ModuleManager.getInstance(project).modules.flatMap { module ->
                val contentRoots = ModuleRootManager.getInstance(module).contentRoots
                contentRoots.map { contentRoot ->
                    WorkspaceFolder().apply {
                        name = folderName(module, contentRoot, isOnlyContentRoot = contentRoots.size == 1)
                        uri = toUriString(contentRoot)
                    }
                }
            }
        }

    /**
     * A module can legitimately own more than one content root (for example DataGrip's attached folders), and
     * [WorkspaceFolder.name] is used by the language server as a path segment, so every folder needs its own name.
     * Modules with a single content root keep the module name they have always been given.
     */
    private fun folderName(module: Module, contentRoot: VirtualFile, isOnlyContentRoot: Boolean): String =
        if (isOnlyContentRoot) module.name else "${module.name}/${contentRoot.name}"
}
