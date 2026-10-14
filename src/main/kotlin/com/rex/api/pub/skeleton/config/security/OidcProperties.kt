package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oidc")
data class OidcProperties(
    val issuer: String = "http://localhost:8080",
    val keyId: String = "default-key-id",
    // RSA 키 설정 (PEM 형식 또는 자동 생성)
    val rsaPrivateKey: String? = null,
    val rsaPublicKey: String? = null,
    // ID Token 설정
    val idTokenExpiration: Long = 3600, // 1시간 (초)
    // 프로덕션 보안 설정 (true면 RSA 키 미설정시 시작 실패)
    val requireRsaKey: Boolean = false,
)