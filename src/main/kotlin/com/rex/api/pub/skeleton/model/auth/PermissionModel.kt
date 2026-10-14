package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** 권한 응답. */
data class PermissionModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("code")
    val code: String,
    @field:JsonProperty("name")
    val name: String,
    @field:JsonProperty("description")
    val description: String?,
    @field:JsonProperty("resourceType")
    val resourceType: String?,
    @field:JsonProperty("action")
    val action: String?,
    @field:JsonProperty("isSystem")
    val isSystem: Boolean,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
)