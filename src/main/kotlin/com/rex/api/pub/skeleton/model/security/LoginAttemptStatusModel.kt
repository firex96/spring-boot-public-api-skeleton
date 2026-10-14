package com.rex.api.pub.skeleton.model.security

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 로그인 시도 상태 Model
 */
data class LoginAttemptStatusModel(
    @field:JsonProperty("username")
    val username: String,
    @field:JsonProperty("currentAttempts")
    val currentAttempts: Int,
    @field:JsonProperty("maxAttempts")
    val maxAttempts: Int,
    @field:JsonProperty("isLocked")
    val isLocked: Boolean,
    @field:JsonProperty("remainingLockTimeSeconds")
    val remainingLockTimeSeconds: Long?,
)