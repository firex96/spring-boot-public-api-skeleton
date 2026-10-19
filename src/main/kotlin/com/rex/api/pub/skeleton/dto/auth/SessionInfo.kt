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
    /** 이 세션을 만든 인증이 끝난 시각(= `user_sessions.created_at`). access 토큰을 재발급해도 바뀌지 않는다 — id_token `auth_time` 의 원천. */
    val authenticatedAt: Instant,
    val revoked: Boolean = false,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}