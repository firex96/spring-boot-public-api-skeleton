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
 * 비밀번호 재설정 토큰 엔티티
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = [
        Index(name = "IX_password_reset_tokens_token_hash", columnList = "token_hash"),
        Index(name = "IX_password_reset_tokens_user_id", columnList = "user_id"),
        Index(name = "IX_password_reset_tokens_expires_at", columnList = "expires_at"),
    ],
)
class PasswordResetTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    /**
     * 토큰의 SHA-256 해시 (원본 토큰은 저장하지 않음)
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,

    @Column(name = "is_used", nullable = false)
    var isUsed: Boolean = false,

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun isValid(): Boolean = !isUsed && LocalDateTime.now().isBefore(expiresAt)

    fun markAsUsed() {
        this.isUsed = true
        this.usedAt = LocalDateTime.now()
    }
}