package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "public-api-skeleton",
    val audience: String = "skeleton-apps",
    val accessTokenExpiration: Long = 3600, // 1시간 (초)
    val refreshTokenExpiration: Long = 604800, // 7일 (초)
)