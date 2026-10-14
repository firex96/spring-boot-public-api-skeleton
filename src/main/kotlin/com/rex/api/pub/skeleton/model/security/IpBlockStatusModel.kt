package com.rex.api.pub.skeleton.model.security

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * IP 차단 상태 확인 응답 Model
 */
data class IpBlockStatusModel(
    @field:JsonProperty("ipAddress")
    val ipAddress: String,
    @field:JsonProperty("isBlocked")
    val isBlocked: Boolean,
    @field:JsonProperty("blockInfo")
    val blockInfo: BlockedIpModel?,
)