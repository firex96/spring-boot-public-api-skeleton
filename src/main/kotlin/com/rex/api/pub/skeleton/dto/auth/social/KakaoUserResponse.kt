package com.rex.api.pub.skeleton.dto.auth.social

// 카카오 API 응답
data class KakaoUserResponse(
    val id: Long,
    val kakao_account: KakaoAccount?,
    val properties: KakaoProperties?,
)