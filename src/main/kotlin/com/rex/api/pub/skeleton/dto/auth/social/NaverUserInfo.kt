package com.rex.api.pub.skeleton.dto.auth.social

data class NaverUserInfo(
    val id: String,
    val email: String?,
    val name: String?,
    val nickname: String?,
    val profile_image: String?,
    val mobile: String?,
)