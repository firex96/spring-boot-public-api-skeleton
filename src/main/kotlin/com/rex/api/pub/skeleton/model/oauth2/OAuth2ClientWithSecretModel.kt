package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** 생성·시크릿 재발급 직후 한 번만 내려가는 평문 시크릿 포함 응답. */
data class OAuth2ClientWithSecretModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("clientId")
    val clientId: String,
    @field:JsonProperty("clientSecret")
    val clientSecret: String,
    @field:JsonProperty("clientName")
    val clientName: String,
    @field:JsonProperty("description")
    val description: String?,
    @field:JsonProperty("grantTypes")
    val grantTypes: List<String>,
    @field:JsonProperty("redirectUris")
    val redirectUris: List<String>,
    @field:JsonProperty("scopes")
    val scopes: List<String>,
    @field:JsonProperty("accessTokenValidity")
    val accessTokenValidity: Int,
    @field:JsonProperty("refreshTokenValidity")
    val refreshTokenValidity: Int,
    @field:JsonProperty("isActive")
    val isActive: Boolean,
    @field:JsonProperty("isFirstParty")
    val isFirstParty: Boolean,
    @field:JsonProperty("requirePkce")
    val requirePkce: Boolean,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
    @field:JsonProperty("updatedAt")
    val updatedAt: LocalDateTime,
)