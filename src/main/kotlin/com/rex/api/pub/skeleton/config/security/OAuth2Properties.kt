package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oauth2")
data class OAuth2Properties(
    val naver: OAuth2ProviderProperties? = null,
    val kakao: OAuth2ProviderProperties? = null,
    val google: OAuth2ProviderProperties? = null,
    val redirectBaseUrl: String = "http://localhost:8080",
)