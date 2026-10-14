package com.rex.api.pub.skeleton.dto.oauth2

data class AuthorizationRequestDto(
    val responseType: String, // code
    val clientId: String,
    val redirectUri: String,
    val scope: String? = null,
    val state: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null, // plain or S256
    val nonce: String? = null,
)