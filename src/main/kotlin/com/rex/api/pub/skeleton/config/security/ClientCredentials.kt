package com.rex.api.pub.skeleton.config.security

/** 토큰 엔드포인트 클라이언트 자격 — `client_secret_basic`(Authorization: Basic) 또는 `client_secret_post`(폼) 에서 꺼낸 값. */
data class ClientCredentials(
    val clientId: String?,
    val clientSecret: String?,
)