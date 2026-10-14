package com.rex.api.pub.skeleton.dto.auth

data class AccessTokenResponseDto(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)