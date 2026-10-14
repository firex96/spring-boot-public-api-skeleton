package com.rex.api.pub.skeleton.dto.oauth2

import jakarta.validation.constraints.NotBlank

data class TokenRequestDto(
    @field:NotBlank(message = "grant_type은 필수입니다")
    val grantType: String,
    val code: String? = null, // authorization_code
    val redirectUri: String? = null, // authorization_code
    val clientId: String? = null,
    val clientSecret: String? = null,
    val refreshToken: String? = null, // refresh_token
    val scope: String? = null, // client_credentials
    val codeVerifier: String? = null, // PKCE
)