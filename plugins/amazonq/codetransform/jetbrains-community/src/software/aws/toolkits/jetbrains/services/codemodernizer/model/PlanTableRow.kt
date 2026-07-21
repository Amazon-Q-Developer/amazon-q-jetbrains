// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlanTableRow(
    @param:JsonProperty("name")
    val name: String?,
    @param:JsonProperty("value")
    val value: String?,
    @param:JsonProperty("dependencyName")
    val dependency: String?,
    @param:JsonProperty("action")
    val action: String?,
    @param:JsonProperty("currentVersion")
    val currentVersion: String?,
    @param:JsonProperty("targetVersion")
    val targetVersion: String?,
    @param:JsonProperty("apiFullyQualifiedName")
    val deprecatedCode: String?,
    @param:JsonProperty("numChangedFiles")
    val filesToBeChanged: String?,
    @param:JsonProperty("relativePath")
    val filePath: String?,
) {
    fun getValueForColumn(col: String): String? =
        when (col) {
            "name" -> name
            "value" -> value
            "dependencyName" -> dependency
            "action" -> action
            "currentVersion" -> currentVersion
            "targetVersion" -> targetVersion
            "apiFullyQualifiedName" -> deprecatedCode
            "numChangedFiles" -> filesToBeChanged
            "relativePath" -> filePath
            else -> "-"
        }
}
