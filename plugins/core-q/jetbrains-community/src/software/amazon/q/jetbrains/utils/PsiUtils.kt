// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.utils

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement

fun PsiElement.isTestOrInjectedText(): Boolean {
    val project = this.project
    val virtualFile = this.containingFile.virtualFile ?: return false
    return this.isInjectedText() || ProjectRootManager.getInstance(project).fileIndex.isInTestSourceContent(virtualFile)
}

fun PsiElement.isInjectedText(): Boolean {
    val virtualFile = this.containingFile.virtualFile ?: return false
    return virtualFile is VirtualFileWindow
}
