package com.rex.api.pub.skeleton.dto.auth

// 소셜 로그인 콜백 요청
data class SocialLoginCallbackRequestDto(
    val code: String,
    val state: String? = null,
    val deviceType: String? = null,
    val deviceInfo: String? = null,
)