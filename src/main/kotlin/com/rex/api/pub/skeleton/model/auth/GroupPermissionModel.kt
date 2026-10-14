package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** 그룹-권한 매핑 응답. */
data class GroupPermissionModel(
    @field:JsonProperty("groupId")
    val groupId: Long,
    @field:JsonProperty("permissionId")
    val permissionId: Long,
    @field:JsonProperty("permissionCode")
    val permissionCode: String,
    @field:JsonProperty("permissionName")
    val permissionName: String,
    @field:JsonProperty("grantedAt")
    val grantedAt: LocalDateTime,
    @field:JsonProperty("grantedBy")
    val grantedBy: Long?,
)