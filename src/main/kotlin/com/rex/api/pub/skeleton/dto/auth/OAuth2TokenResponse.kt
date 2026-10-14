package com.rex.api.pub.skeleton.dto.auth

// Provider별 토큰 응답
data class OAuth2TokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
)