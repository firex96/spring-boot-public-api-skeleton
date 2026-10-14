package com.rex.api.pub.skeleton.model.security

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 계정 잠금 해제 요청 Model
 */
data class UnlockAccountRequestModel(
    @field:JsonProperty("username")
    val username: String,
)