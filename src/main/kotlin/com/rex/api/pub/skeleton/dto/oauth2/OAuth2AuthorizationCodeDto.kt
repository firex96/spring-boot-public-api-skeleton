package com.rex.api.pub.skeleton.dto.oauth2

import java.time.LocalDateTime

data class OAuth2AuthorizationCodeDto(
    val id: Long,
    val code: String,
    val clientId: String,
    val userId: Long,
    val redirectUri: String,
    val scopes: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val state: String?,
    val nonce: String?,
    val expiresAt: LocalDateTime,
    val isUsed: Boolean,
    val usedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)