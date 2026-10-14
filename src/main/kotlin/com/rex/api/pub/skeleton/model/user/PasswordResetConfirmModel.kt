package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 비밀번호 재설정 완료 Model
 */
data class PasswordResetConfirmModel(
    @field:JsonProperty("token")
    val token: String,
    @field:JsonProperty("newPassword")
    val newPassword: String,
)