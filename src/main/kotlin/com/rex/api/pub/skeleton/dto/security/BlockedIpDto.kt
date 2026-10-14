package com.rex.api.pub.skeleton.dto.security

import com.rex.api.pub.skeleton.enums.auth.BlockReason
import java.time.LocalDateTime

/**
 * 차단된 IP DTO
 * Entity ↔ DTO 변환은 SecurityMapper에서 담당
 */
data class BlockedIpDto(
    val id: Long = 0,
    val ipAddress: String,
    val blockReason: BlockReason,
    val description: String? = null,
    val blockedAt: LocalDateTime = LocalDateTime.now(),
    val expiresAt: LocalDateTime? = null,
    val blockedBy: Long? = null,
    val isActive: Boolean = true,
    val unblockedAt: LocalDateTime? = null,
    val unblockedBy: Long? = null,
) {
    val isPermanent: Boolean
        get() = expiresAt == null && isActive
}