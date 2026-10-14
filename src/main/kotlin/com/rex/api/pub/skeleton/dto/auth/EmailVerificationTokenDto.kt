package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

data class EmailVerificationTokenDto(
    val id: Long,
    val userId: Long,
    val email: String,
    val tokenHash: String,
    val expiresAt: LocalDateTime,
    val isVerified: Boolean,
    val verifiedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)