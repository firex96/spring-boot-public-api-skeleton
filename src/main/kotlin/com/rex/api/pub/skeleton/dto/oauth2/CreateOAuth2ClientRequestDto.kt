package com.rex.api.pub.skeleton.dto.oauth2

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateOAuth2ClientRequestDto(
    @field:NotBlank(message = "클라이언트 이름은 필수입니다")
    @field:Size(max = 100)
    val clientName: String,
    @field:Size(max = 500)
    val description: String? = null,
    val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    val redirectUris: List<String> = emptyList(),
    val scopes: List<String> = listOf("openid", "profile", "email"),
    val accessTokenValidity: Int = 3600,
    val refreshTokenValidity: Int = 604800,
    val isFirstParty: Boolean = false,
    val requirePkce: Boolean = false,
)