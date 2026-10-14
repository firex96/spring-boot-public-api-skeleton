package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 역할에 여러 사용자 할당 요청
data class AssignUsersToRoleRequestDto(
    val userIds: List<Long>,
    val expiresAt: LocalDateTime? = null,
)