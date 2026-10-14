package com.rex.api.pub.skeleton.dto.security

data class LoginAttemptStatusDto(
    val username: String,
    val currentAttempts: Int,
    val maxAttempts: Int,
    val isLocked: Boolean,
    val remainingLockTimeSeconds: Long?,
)