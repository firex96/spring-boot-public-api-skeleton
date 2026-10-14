package com.rex.api.pub.skeleton.dto.oauth2

import java.time.LocalDateTime

data class OAuth2AccessTokenDto(
    val id: Long,
    val tokenId: String,
    val clientId: String,
    val userId: Long?,
    val scopes: String?,
    val tokenType: String,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val isRevoked: Boolean,
    val revokedAt: LocalDateTime?,
    val refreshTokenId: String?,
)