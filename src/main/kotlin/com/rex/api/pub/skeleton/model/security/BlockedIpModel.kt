package com.rex.api.pub.skeleton.model.security

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import java.time.LocalDateTime

/**
 * 차단된 IP Model
 * Controller 레이어에서 사용
 */
data class BlockedIpModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("ipAddress")
    val ipAddress: String,
    @field:JsonProperty("blockReason")
    val blockReason: BlockReason,
    @field:JsonProperty("description")
    val description: String?,
    @field:JsonProperty("blockedAt")
    val blockedAt: LocalDateTime,
    @field:JsonProperty("expiresAt")
    val expiresAt: LocalDateTime?,
    @field:JsonProperty("blockedBy")
    val blockedBy: Long?,
    @field:JsonProperty("isActive")
    val isActive: Boolean,
    @field:JsonProperty("unblockedAt")
    val unblockedAt: LocalDateTime?,
    @field:JsonProperty("unblockedBy")
    val unblockedBy: Long?,
    @field:JsonProperty("isPermanent")
    val isPermanent: Boolean,
)