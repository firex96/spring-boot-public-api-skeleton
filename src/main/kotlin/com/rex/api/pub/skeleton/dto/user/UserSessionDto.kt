package com.rex.api.pub.skeleton.dto.user

import java.time.LocalDateTime

/**
 * 사용자 세션 DTO
 * Entity ↔ DTO 변환은 AuthMapper에서 담당
 */
data class UserSessionDto(
    val id: Long,
    val sessionId: String, // 마스킹된 세션 ID
    val deviceType: String?,
    val deviceInfo: String?,
    val ipAddress: String?,
    val lastActivityAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val isCurrentSession: Boolean = false,
)

// ===== 프로필 =====