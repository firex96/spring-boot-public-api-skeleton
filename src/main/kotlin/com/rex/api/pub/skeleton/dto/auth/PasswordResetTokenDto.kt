package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

data class PasswordResetTokenDto(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: LocalDateTime,
    val isUsed: Boolean,
    val usedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)