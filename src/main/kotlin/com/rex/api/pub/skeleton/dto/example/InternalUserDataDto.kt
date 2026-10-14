package com.rex.api.pub.skeleton.dto.example

import com.rex.api.pub.skeleton.enums.example.UserStatus
import java.time.LocalDateTime

/**
 * 내부 시스템 사용자 데이터 DTO.
 */
data class InternalUserDataDto(
    val userId: String,
    val registeredAt: LocalDateTime?,
    val status: UserStatus,
)