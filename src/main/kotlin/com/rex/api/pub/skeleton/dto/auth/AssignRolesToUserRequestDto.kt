package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

// 사용자에 여러 역할 할당 요청
data class AssignRolesToUserRequestDto(
    val roleIds: List<Long>,
    val expiresAt: LocalDateTime? = null,
)