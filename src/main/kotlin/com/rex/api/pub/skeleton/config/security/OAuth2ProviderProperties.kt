package com.rex.api.pub.skeleton.config.security

data class OAuth2ProviderProperties(
    val clientId: String,
    val clientSecret: String,
    val scope: List<String> = emptyList(),
)