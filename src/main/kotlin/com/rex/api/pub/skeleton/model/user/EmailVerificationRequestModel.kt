package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 이메일 인증 요청 Model
 */
data class EmailVerificationRequestModel(
    @field:JsonProperty("email")
    val email: String,
)