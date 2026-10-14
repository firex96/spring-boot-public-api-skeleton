package com.rex.api.pub.skeleton.dto.oauth2

data class OAuth2ErrorResponseDto(
    val error: String,
    val errorDescription: String? = null,
    val errorUri: String? = null,
)