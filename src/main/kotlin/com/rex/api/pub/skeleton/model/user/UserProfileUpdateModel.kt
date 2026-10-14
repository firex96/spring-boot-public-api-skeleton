package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 프로필 업데이트 요청 Model
 */
data class UserProfileUpdateModel(
    @field:JsonProperty("name")
    val name: String?,
    @field:JsonProperty("nickname")
    val nickname: String?,
    @field:JsonProperty("phone")
    val phone: String?,
    @field:JsonProperty("profileImage")
    val profileImage: String?,
)