package com.rex.api.pub.skeleton.dto.auth.social

// 네이버 API 응답
data class NaverUserResponse(
    val resultcode: String,
    val message: String,
    val response: NaverUserInfo?,
)