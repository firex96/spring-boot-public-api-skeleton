package com.rex.api.pub.skeleton.dto.auth

import java.io.Serializable
import java.time.Instant

data class SessionInfo(
    val userId: Long,
    val sessionId: String,
    val groups: List<String>,
    val permissions: List<String>,
    val deviceType: String?,
    val ipAddress: String?,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revoked: Boolean = false,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}