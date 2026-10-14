package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.SocialProvider

// 소셜 계정 연동 정보
data class SocialAccountDto(
    val provider: SocialProvider,
    val providerId: String,
    val email: String?,
    val name: String?,
    val connectedAt: java.time.LocalDateTime,
)