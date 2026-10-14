package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** 그룹(Role) 응답 — `/admin/groups` · `/admin/users/{id}/groups`. */
data class GroupModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("code")
    val code: String,
    @field:JsonProperty("name")
    val name: String,
    @field:JsonProperty("description")
    val description: String?,
    @field:JsonProperty("isSystem")
    val isSystem: Boolean,
    @field:JsonProperty("isDefault")
    val isDefault: Boolean,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
    @field:JsonProperty("updatedAt")
    val updatedAt: LocalDateTime,
)