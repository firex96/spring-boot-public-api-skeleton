package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * OAuth2 Refresh Token
 *
 * 발급된 Refresh Token 정보 저장
 */
@Entity
@Table(name = "oauth2_refresh_tokens")
class OAuth2RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    // 토큰 식별자
    @Column(name = "token_id", nullable = false, unique = true, length = 64)
    var tokenId: String,

    @Column(name = "client_id", nullable = false, length = 64)
    var clientId: String,

    // 사용자 ID
    @Column(name = "user_id")
    var userId: Long? = null,

    // 허용된 scopes
    @Column(name = "scopes", length = 500)
    var scopes: String? = null,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "is_revoked", nullable = false)
    var isRevoked: Boolean = false,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,
) {
    fun getScopeList(): List<String> = scopes?.split(",")?.map { it.trim() } ?: emptyList()

    fun isExpired(): Boolean = expiresAt.isBefore(LocalDateTime.now())

    fun isActive(): Boolean = !isRevoked && !isExpired()
}