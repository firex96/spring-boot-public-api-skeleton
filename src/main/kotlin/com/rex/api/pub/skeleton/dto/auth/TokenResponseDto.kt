package com.rex.api.pub.skeleton.dto.auth

data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)