package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * 사용자 프로필 Model
 * Controller 레이어에서 사용
 */
data class UserProfileModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("loginId")
    val loginId: String,
    @field:JsonProperty("email")
    val email: String?,
    @field:JsonProperty("emailVerified")
    val emailVerified: Boolean,
    @field:JsonProperty("phone")
    val phone: String?,
    @field:JsonProperty("name")
    val name: String?,
    @field:JsonProperty("nickname")
    val nickname: String?,
    @field:JsonProperty("profileImage")
    val profileImage: String?,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
)