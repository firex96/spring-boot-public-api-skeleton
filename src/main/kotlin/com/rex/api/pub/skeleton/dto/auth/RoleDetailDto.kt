package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 역할 상세 응답 (권한 포함)
data class RoleDetailDto(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val isSystem: Boolean,
    val isDefault: Boolean,
    val permissions: List<PermissionDto>,
    val userCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)