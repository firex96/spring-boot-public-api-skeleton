package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank

// 소셜 로그인 요청
data class SocialLoginRequestDto(
    @field:NotBlank(message = "Authorization code는 필수입니다")
    val code: String,
    val deviceType: String? = null,
    val deviceInfo: String? = null,
)