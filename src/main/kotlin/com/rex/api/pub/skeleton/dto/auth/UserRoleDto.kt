package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 사용자-역할 매핑 응답
data class UserRoleDto(
    val userId: Long,
    val roleId: Long,
    val roleCode: String,
    val roleName: String,
    val assignedAt: LocalDateTime,
    val assignedBy: Long?,
    val expiresAt: LocalDateTime?,
)