package com.rex.api.pub.skeleton.dto.auth

data class UserInfoDto(
    val id: Long,
    val loginId: String,
    val email: String?,
    val name: String?,
    val nickname: String?,
    val phone: String?,
    val profileImage: String?,
    val groups: List<String>,
)