package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * OAuth2 Access Token
 *
 * 발급된 Access Token 정보 저장 (Introspection 및 Revocation 용)
 */
@Entity
@Table(name = "oauth2_access_tokens")
class OAuth2AccessTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    // 토큰 식별자 (JTI - JWT ID)
    @Column(name = "token_id", nullable = false, unique = true, length = 64)
    var tokenId: String,

    @Column(name = "client_id", nullable = false, length = 64)
    var clientId: String,

    // 사용자 ID (client_credentials flow에서는 null)
    @Column(name = "user_id")
    var userId: Long? = null,

    // 허용된 scopes
    @Column(name = "scopes", length = 500)
    var scopes: String? = null,

    // 토큰 타입 (Bearer)
    @Column(name = "token_type", nullable = false, length = 20)
    var tokenType: String = "Bearer",

    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "is_revoked", nullable = false)
    var isRevoked: Boolean = false,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    // 연관된 Refresh Token ID
    @Column(name = "refresh_token_id", length = 64)
    var refreshTokenId: String? = null,
) {
    fun getScopeList(): List<String> = scopes?.split(",")?.map { it.trim() } ?: emptyList()

    fun isExpired(): Boolean = expiresAt.isBefore(LocalDateTime.now())

    fun isActive(): Boolean = !isRevoked && !isExpired()
}