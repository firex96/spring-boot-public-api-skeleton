package com.rex.api.pub.skeleton.dto.auth

import java.time.Instant

data class TokenPayload(
    val userId: Long,
    val sessionId: String,
    val groups: List<String>,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val isRefreshToken: Boolean = false,
)