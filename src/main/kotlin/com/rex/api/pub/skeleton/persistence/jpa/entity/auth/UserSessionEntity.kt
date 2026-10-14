package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_sessions")
class UserSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    var sessionId: String,

    @Column(name = "device_type", length = 20)
    var deviceType: String? = null,

    @Column(name = "device_info", length = 500)
    var deviceInfo: String? = null,

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,

    @Column(name = "refresh_token_hash", nullable = false, length = 255)
    var refreshTokenHash: String,

    @Column(name = "access_token_issued_at", nullable = false)
    var accessTokenIssuedAt: LocalDateTime,

    @Column(name = "refresh_token_issued_at", nullable = false)
    var refreshTokenIssuedAt: LocalDateTime,

    @Column(name = "access_token_expires_at", nullable = false)
    var accessTokenExpiresAt: LocalDateTime,

    @Column(name = "refresh_token_expires_at", nullable = false)
    var refreshTokenExpiresAt: LocalDateTime,

    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: LocalDateTime,

    @Column(name = "is_revoked", nullable = false)
    var isRevoked: Boolean = false,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Column(name = "revoked_reason", length = 100)
    var revokedReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun isActive(): Boolean = !isRevoked && LocalDateTime.now().isBefore(refreshTokenExpiresAt)

    fun revoke(reason: String) {
        this.isRevoked = true
        this.revokedAt = LocalDateTime.now()
        this.revokedReason = reason
    }
}