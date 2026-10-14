package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 권한 응답
data class PermissionDto(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val resourceType: String?,
    val action: String?,
    val isSystem: Boolean,
    val createdAt: LocalDateTime,
)