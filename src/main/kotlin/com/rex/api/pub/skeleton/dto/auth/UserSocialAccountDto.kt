package com.rex.api.pub.skeleton.dto.auth

import java.time.LocalDateTime

/** 소셜 연동 계정 내부 DTO — 토큰 포함. 응답은 `SocialAccountDto` → `SocialAccountModel`. */
data class UserSocialAccountDto(
    val id: Long,
    val userId: Long,
    val provider: String,
    val providerId: String,
    val email: String?,
    val name: String?,
    val profileImage: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenExpiresAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)