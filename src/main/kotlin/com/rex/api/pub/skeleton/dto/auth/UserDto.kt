package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.UserStatus
import java.time.LocalDateTime

/**
 * 사용자 내부 DTO — EntityService 가 BizService 에 돌려주는 전체 필드(비밀번호 해시 포함).
 * 응답에 쓰지 않는다(응답은 `UserInfoModel`·`UserProfileModel`).
 */
data class UserDto(
    val id: Long,
    val loginId: String,
    val passwordHash: String?,
    val email: String?,
    val phone: String?,
    val name: String?,
    val nickname: String?,
    val profileImage: String?,
    val status: UserStatus,
    val lastLoginAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)