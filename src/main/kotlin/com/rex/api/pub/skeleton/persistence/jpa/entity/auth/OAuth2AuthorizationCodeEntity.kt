package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * OAuth2 Authorization Code
 *
 * Authorization Code Flow에서 사용되는 임시 인증 코드
 */
@Entity
@Table(name = "oauth2_authorization_codes")
class OAuth2AuthorizationCodeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "code", nullable = false, unique = true, length = 64)
    var code: String,

    @Column(name = "client_id", nullable = false, length = 64)
    var clientId: String,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "redirect_uri", nullable = false, length = 500)
    var redirectUri: String,

    // 요청된 scopes (콤마로 구분)
    @Column(name = "scopes", length = 500)
    var scopes: String? = null,

    // PKCE code_challenge
    @Column(name = "code_challenge", length = 128)
    var codeChallenge: String? = null,

    // PKCE code_challenge_method (plain or S256)
    @Column(name = "code_challenge_method", length = 10)
    var codeChallengeMethod: String? = null,

    // state 파라미터
    @Column(name = "state", length = 256)
    var state: String? = null,

    // nonce 파라미터 (OpenID Connect)
    @Column(name = "nonce", length = 256)
    var nonce: String? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    // 사용자가 실제로 인증한 시각 — id_token auth_time 의 원천. 배포 전 발급분은 NULL
    @Column(name = "auth_time")
    var authTime: LocalDateTime? = null,

    @Column(name = "is_used", nullable = false)
    var isUsed: Boolean = false,

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun getScopeList(): List<String> = scopes?.split(",")?.map { it.trim() } ?: emptyList()

    fun isExpired(): Boolean = expiresAt.isBefore(LocalDateTime.now())

    fun isValid(): Boolean = !isUsed && !isExpired()
}