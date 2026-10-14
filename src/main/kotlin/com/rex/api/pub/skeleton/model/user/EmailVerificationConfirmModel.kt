package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 이메일 인증 완료 Model
 */
data class EmailVerificationConfirmModel(
    @field:JsonProperty("token")
    val token: String,
)