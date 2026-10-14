package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** 그룹 상세 응답 — 권한 목록·사용자 수 포함. */
data class GroupDetailModel(
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
    @field:JsonProperty("permissions")
    val permissions: List<PermissionModel>,
    @field:JsonProperty("userCount")
    val userCount: Int,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
    @field:JsonProperty("updatedAt")
    val updatedAt: LocalDateTime,
)