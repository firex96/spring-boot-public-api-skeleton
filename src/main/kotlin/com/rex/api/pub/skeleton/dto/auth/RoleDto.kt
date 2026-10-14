package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 역할 응답
data class RoleDto(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val isSystem: Boolean,
    val isDefault: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)