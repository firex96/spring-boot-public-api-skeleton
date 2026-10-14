package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 역할에 사용자 할당 요청
data class AssignUserToRoleRequestDto(
    val userId: Long,
    val expiresAt: LocalDateTime? = null,
)