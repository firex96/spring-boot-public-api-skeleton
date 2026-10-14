package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 비밀번호 변경 Model
 */
data class PasswordChangeModel(
    @field:JsonProperty("currentPassword")
    val currentPassword: String,
    @field:JsonProperty("newPassword")
    val newPassword: String,
)