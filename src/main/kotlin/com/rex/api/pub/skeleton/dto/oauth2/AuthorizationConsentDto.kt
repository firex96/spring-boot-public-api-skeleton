package com.rex.api.pub.skeleton.dto.oauth2

data class AuthorizationConsentDto(
    val clientId: String,
    val clientName: String,
    val requestedScopes: List<String>,
    val redirectUri: String,
    val state: String?,
)

// ===== Token Endpoint DTO =====