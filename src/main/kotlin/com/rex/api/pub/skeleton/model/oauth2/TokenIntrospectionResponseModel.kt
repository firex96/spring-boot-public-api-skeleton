package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty

/** 토큰 검사 응답(RFC 7662) — `POST /oauth2/introspect`. 필드 이름은 기존 DTO 계약(camelCase) 그대로. */
data class TokenIntrospectionResponseModel(
    @field:JsonProperty("active")
    val active: Boolean,
    @field:JsonProperty("scope")
    val scope: String?,
    @field:JsonProperty("clientId")
    val clientId: String?,
    @field:JsonProperty("username")
    val username: String?,
    @field:JsonProperty("tokenType")
    val tokenType: String?,
    @field:JsonProperty("exp")
    val exp: Long?,
    @field:JsonProperty("iat")
    val iat: Long?,
    @field:JsonProperty("nbf")
    val nbf: Long?,
    @field:JsonProperty("sub")
    val sub: String?,
    @field:JsonProperty("aud")
    val aud: String?,
    @field:JsonProperty("iss")
    val iss: String?,
    @field:JsonProperty("jti")
    val jti: String?,
)