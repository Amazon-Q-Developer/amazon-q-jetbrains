// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "tree")
data class SctMetadata(
    @param:JsonProperty("instances")
    val instances: Instances,
)

data class Instances(
    @param:JsonProperty("ProjectModel")
    val projectModel: ProjectModel,
)

data class ProjectModel(
    @param:JsonProperty("entities")
    val entities: Entities,
    @param:JsonProperty("relations")
    val relations: Relations,
)

data class Entities(
    @param:JsonProperty("sources")
    val sources: Sources,
    @param:JsonProperty("targets")
    val targets: Targets,
)

data class Sources(
    @param:JsonProperty("DbServer")
    val dbServer: DbServer,
)

data class Targets(
    @param:JsonProperty("DbServer")
    val dbServer: DbServer,
)

data class DbServer(
    @param:JsonProperty("vendor")
    val vendor: String,
    @param:JsonProperty("name")
    val name: String,
)

data class Relations(
    @param:JsonProperty("server-node-location")
    @param:JacksonXmlElementWrapper(useWrapping = false)
    val serverNodeLocation: List<ServerNodeLocation>,
)

data class ServerNodeLocation(
    @param:JsonProperty("FullNameNodeInfoList")
    val fullNameNodeInfoList: FullNameNodeInfoList,
)

data class FullNameNodeInfoList(
    @param:JsonProperty("nameParts")
    val nameParts: NameParts,
)

data class NameParts(
    @param:JsonProperty("FullNameNodeInfo")
    @param:JacksonXmlElementWrapper(useWrapping = false)
    val fullNameNodeInfo: List<FullNameNodeInfo>,
)

data class FullNameNodeInfo(
    @param:JsonProperty("typeNode")
    val typeNode: String,
    @param:JsonProperty("nameNode")
    val nameNode: String,
)
