package com.rex.api.pub.skeleton.dto.user

import java.time.LocalDateTime

data class UserProfileDto(
    val id: Long,
    val loginId: String,
    val email: String?,
    val emailVerified: Boolean,
    val phone: String?,
    val name: String?,
    val nickname: String?,
    val profileImage: String?,
    val createdAt: LocalDateTime,
)