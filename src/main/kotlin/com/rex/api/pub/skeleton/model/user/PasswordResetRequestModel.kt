package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 비밀번호 재설정 요청 Model
 */
data class PasswordResetRequestModel(
    @field:JsonProperty("email")
    val email: String,
)