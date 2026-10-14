package com.rex.api.pub.skeleton.dto.oauth2

import jakarta.validation.constraints.NotBlank

data class TokenIntrospectionRequestDto(
    @field:NotBlank(message = "token은 필수입니다")
    val token: String,
    val tokenTypeHint: String? = null, // access_token or refresh_token
)