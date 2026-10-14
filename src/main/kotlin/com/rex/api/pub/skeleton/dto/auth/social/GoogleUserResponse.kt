package com.rex.api.pub.skeleton.dto.auth.social

// 구글 API 응답
data class GoogleUserResponse(
    val id: String,
    val email: String?,
    val name: String?,
    val picture: String?,
)