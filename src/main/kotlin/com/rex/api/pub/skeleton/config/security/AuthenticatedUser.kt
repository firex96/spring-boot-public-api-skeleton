package com.rex.api.pub.skeleton.config.security

data class AuthenticatedUser(
    val userId: Long,
    val sessionId: String,
    val groups: List<String>,
    val permissions: List<String>,
)