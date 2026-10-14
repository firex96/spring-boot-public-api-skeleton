package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** OAuth2 클라이언트 응답 — `/admin/oauth2/clients`. */
data class OAuth2ClientModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("clientId")
    val clientId: String,
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