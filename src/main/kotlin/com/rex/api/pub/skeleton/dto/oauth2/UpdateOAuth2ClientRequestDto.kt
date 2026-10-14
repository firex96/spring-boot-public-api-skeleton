package com.rex.api.pub.skeleton.dto.oauth2

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateOAuth2ClientRequestDto(
    @field:NotBlank(message = "클라이언트 이름은 필수입니다")
    @field:Size(max = 100)
    val clientName: String,
    @field:Size(max = 500)
    val description: String? = null,
    val grantTypes: List<String>? = null,
    val redirectUris: List<String>? = null,
    val scopes: List<String>? = null,
    val accessTokenValidity: Int? = null,
    val refreshTokenValidity: Int? = null,
    val isFirstParty: Boolean? = null,
    val requirePkce: Boolean? = null,
    val isActive: Boolean? = null,
)