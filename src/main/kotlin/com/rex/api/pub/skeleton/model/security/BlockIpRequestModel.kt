package com.rex.api.pub.skeleton.model.security

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.auth.BlockReason

/**
 * IP 차단 요청 Model
 */
data class BlockIpRequestModel(
    @field:JsonProperty("ipAddress")
    val ipAddress: String,
    @field:JsonProperty("reason")
    val reason: BlockReason = BlockReason.MANUAL,
    @field:JsonProperty("description")
    val description: String? = null,
    @field:JsonProperty("durationMinutes")
    val durationMinutes: Int? = null,
)