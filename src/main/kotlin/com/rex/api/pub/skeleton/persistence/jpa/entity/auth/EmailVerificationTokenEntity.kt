package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 이메일 인증 토큰 엔티티
 */
@Entity
@Table(
    name = "email_verification_tokens",
    indexes = [
        Index(name = "IX_email_verify_tokens_token_hash", columnList = "token_hash"),
        Index(name = "IX_email_verify_tokens_user_id", columnList = "user_id"),
        Index(name = "IX_email_verify_tokens_expires_at", columnList = "expires_at"),
    ],
)
class EmailVerificationTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "email", nullable = false, length = 255)
    var email: String,

    /**
     * 토큰의 SHA-256 해시 (원본 토큰은 저장하지 않음)
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "is_verified", nullable = false)
    var isVerified: Boolean = false,

    @Column(name = "verified_at")
    var verifiedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun isValid(): Boolean = !isVerified && LocalDateTime.now().isBefore(expiresAt)

    fun markAsVerified() {
        this.isVerified = true
        this.verifiedAt = LocalDateTime.now()
    }
}