package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.SocialProvider

// 소셜 사용자 정보 (Provider에서 받아온 정보)
data class SocialUserInfo(
    val provider: SocialProvider,
    val providerId: String,
    val email: String?,
    val name: String?,
    val profileImage: String?,
)