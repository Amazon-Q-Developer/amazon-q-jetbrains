// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

        assertThat(resolved).isNotNull
        // Path.startsWith / endsWith are component-wise and do not touch the filesystem.
        assertThat(resolved!!.startsWith(root())).isTrue()
        assertThat(resolved.endsWith(Path("src", "main", "App.kt"))).isTrue()
    }

    @Test
    fun `resolves a path that has no project-name prefix`() {
        // substringAfter returns the whole string when the project name is absent, so the finding
        // is still resolved relative to the project root.
        val resolved = resolveScanFindingFile(root().toString(), projectName, "src/App.kt")

        assertThat(resolved).isNotNull
        assertThat(resolved!!.startsWith(root())).isTrue()
    }

    @Test
    fun `keeps a path whose dot-dot segments stay inside the root`() {
        val resolved = resolveScanFindingFile(root().toString(), projectName, "$projectName/src/../src/App.kt")

        assertThat(resolved).isEqualTo(root().resolve(Path("src", "App.kt")))
    }

    @Test
    fun `rejects a path that escapes the root via dot-dot`() {
        val maliciousPath = "$projectName/../../../etc/passwd"

        // Red control: the unchecked resolution the old code performed escaped the project root.
        val naive = Path(root().toString(), maliciousPath.substringAfter(projectName)).toAbsolutePath().normalize()
        assertThat(naive.startsWith(root())).isFalse()

        // The hardened resolver refuses to map the escaping path to a file.
        assertThat(resolveScanFindingFile(root().toString(), projectName, maliciousPath)).isNull()
    }

    @Test
    fun `rejects a sibling directory that shares the root name prefix`() {
        // "<root>/../<rootName>-evil/secret" normalizes to a sibling of the root. A component-wise
        // containment check must reject it; a plain string-prefix check would wrongly accept it.
        val resolved = resolveScanFindingFile(
            root().toString(),
            projectName,
            "$projectName/../${projectRoot.root.name}-evil/secret"
        )

        assertThat(resolved).isNull()
    }

    @Test
    fun `rejects a path that resolves to the root itself`() {
        val resolved = resolveScanFindingFile(root().toString(), projectName, projectName)

        assertThat(resolved).isNull()
    }
}
