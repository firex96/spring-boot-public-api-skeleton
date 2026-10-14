package com.rex.api.pub.skeleton.dto.oauth2

import java.time.LocalDateTime

data class OAuth2ClientDto(
    val id: Long,
    val clientId: String,
    val clientName: String,
    val description: String?,
    val grantTypes: List<String>,
    val redirectUris: List<String>,
    val scopes: List<String>,
    val accessTokenValidity: Int,
    val refreshTokenValidity: Int,
    val isActive: Boolean,
    val isFirstParty: Boolean,
    val requirePkce: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)