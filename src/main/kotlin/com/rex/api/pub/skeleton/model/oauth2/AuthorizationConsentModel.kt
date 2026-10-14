package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty

/** 동의 화면 정보 응답 — `GET /oauth2/consent`. */
data class AuthorizationConsentModel(
    @field:JsonProperty("clientId")
    val clientId: String,
    @field:JsonProperty("clientName")
    val clientName: String,
    @field:JsonProperty("requestedScopes")
    val requestedScopes: List<String>,
    @field:JsonProperty("redirectUri")
    val redirectUri: String,
    @field:JsonProperty("state")
    val state: String?,
)