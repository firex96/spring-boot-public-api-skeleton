package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

/**
 * 사용자 세션 내부 DTO — 마스킹 없는 세션 행 그대로. 응답용 `UserSessionDto`(마스킹) 와 구별한다.
 */
data class UserSessionRecordDto(
    val id: Long,
    val userId: Long,
    val sessionId: String,
    val deviceType: String?,
    val deviceInfo: String?,
    val ipAddress: String?,
    val refreshTokenHash: String,
    val accessTokenIssuedAt: LocalDateTime,
    val refreshTokenIssuedAt: LocalDateTime,
    val accessTokenExpiresAt: LocalDateTime,
    val refreshTokenExpiresAt: LocalDateTime,
    val lastActivityAt: LocalDateTime,
    val isRevoked: Boolean,
    val revokedAt: LocalDateTime?,
    val revokedReason: String?,
    val createdAt: LocalDateTime,
)