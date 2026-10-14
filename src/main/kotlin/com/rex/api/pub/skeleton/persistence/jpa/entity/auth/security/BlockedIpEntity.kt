package com.rex.api.pub.skeleton.persistence.jpa.entity.auth.security

import com.rex.api.pub.skeleton.enums.auth.BlockReason
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 차단된 IP 엔티티
 */
@Entity
@Table(
    name = "blocked_ips",
    indexes = [
        Index(name = "IX_blocked_ips_ip_address", columnList = "ip_address"),
        Index(name = "IX_blocked_ips_expires_at", columnList = "expires_at"),
    ],
)
class BlockedIpEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "ip_address", nullable = false, length = 45)
    var ipAddress: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "block_reason", nullable = false, length = 50)
    var blockReason: BlockReason,

    @Column(name = "description", length = 500)
    var description: String? = null,

    @Column(name = "blocked_at", nullable = false)
    var blockedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null, // null이면 영구 차단

    @Column(name = "blocked_by")
    var blockedBy: Long? = null, // 관리자가 수동으로 차단한 경우

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "unblocked_at")
    var unblockedAt: LocalDateTime? = null,

    @Column(name = "unblocked_by")
    var unblockedBy: Long? = null,
) {
    fun isExpired(): Boolean {
        if (!isActive) return true
        if (expiresAt == null) return false
        return LocalDateTime.now().isAfter(expiresAt)
    }

    fun unblock(unblockedBy: Long?) {
        this.isActive = false
        this.unblockedAt = LocalDateTime.now()
        this.unblockedBy = unblockedBy
    }
}