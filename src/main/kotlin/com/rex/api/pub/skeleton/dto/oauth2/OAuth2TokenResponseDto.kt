package com.rex.api.pub.skeleton.dto.oauth2

data class OAuth2TokenResponseDto(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshToken: String? = null,
    val scope: String? = null,
)

// ===== Token Introspection DTO =====