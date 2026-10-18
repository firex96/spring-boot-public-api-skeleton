package com.rex.api.pub.skeleton.dto.oidc

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserInfoDto(
    val sub: String,
    val name: String? = null,
    val nickname: String? = null,
    val picture: String? = null,
    val email: String? = null,
    @JsonProperty("email_verified")
    val emailVerified: Boolean? = null,
    @JsonProperty("updated_at")
    val updatedAt: Long? = null,
)

// ===== Token Response with ID Token =====