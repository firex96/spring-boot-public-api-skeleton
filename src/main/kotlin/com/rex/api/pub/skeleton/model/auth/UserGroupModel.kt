package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** 사용자-그룹 매핑 응답(`dto.auth.UserRoleDto` = `UserRoleDto` 와 필드 동일). */
data class UserGroupModel(
    @field:JsonProperty("userId")
    val userId: Long,
    @field:JsonProperty("roleId")
    val roleId: Long,
    @field:JsonProperty("roleCode")
    val roleCode: String,
    @field:JsonProperty("roleName")
    val roleName: String,
    @field:JsonProperty("assignedAt")
    val assignedAt: LocalDateTime,
    @field:JsonProperty("assignedBy")
    val assignedBy: Long?,
    @field:JsonProperty("expiresAt")
    val expiresAt: LocalDateTime?,
)