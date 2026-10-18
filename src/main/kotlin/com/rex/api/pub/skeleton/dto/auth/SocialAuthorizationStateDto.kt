package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.SocialProvider

/** 발급한 소셜 state 가 묶인 브라우저 세션과 provider — Redis `browser:social-state:{state}` 의 값. */
data class SocialAuthorizationStateDto(
    val browserSessionId: String,
    val provider: SocialProvider,
)