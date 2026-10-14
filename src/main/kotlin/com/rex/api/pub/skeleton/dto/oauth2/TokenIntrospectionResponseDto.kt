package com.rex.api.pub.skeleton.dto.oauth2

data class TokenIntrospectionResponseDto(
    val active: Boolean,
    val scope: String? = null,
    val clientId: String? = null,
    val username: String? = null,
    val tokenType: String? = null,
    val exp: Long? = null, // expiration time
    val iat: Long? = null, // issued at
    val nbf: Long? = null, // not before
    val sub: String? = null, // subject (user id)
    val aud: String? = null, // audience
    val iss: String? = null, // issuer
    val jti: String? = null, // token id
)

// ===== Token Revocation DTO =====