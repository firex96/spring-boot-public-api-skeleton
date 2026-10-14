package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * 사용자 세션 Model
 */
data class UserSessionModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("sessionId")
    val sessionId: String,
    @field:JsonProperty("deviceType")
    val deviceType: String?,
    @field:JsonProperty("deviceInfo")
    val deviceInfo: String?,
    @field:JsonProperty("ipAddress")
    val ipAddress: String?,
    @field:JsonProperty("lastActivityAt")
    val lastActivityAt: LocalDateTime,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
    @field:JsonProperty("isCurrentSession")
    val isCurrentSession: Boolean,
)