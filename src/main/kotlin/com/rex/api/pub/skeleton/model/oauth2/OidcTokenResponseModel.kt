package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/** 토큰 엔드포인트 응답(RFC 6749 §5.1 · OIDC `id_token`) — `POST /oauth2/token`. null 필드는 내려가지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OidcTokenResponseModel(
    @field:JsonProperty("access_token")
    val accessToken: String,
    @field:JsonProperty("token_type")
    val tokenType: String,
    @field:JsonProperty("expires_in")
    val expiresIn: Long,
    @field:JsonProperty("refresh_token")
    val refreshToken: String?,
    @field:JsonProperty("scope")
    val scope: String?,
    @field:JsonProperty("id_token")
    val idToken: String?,
)