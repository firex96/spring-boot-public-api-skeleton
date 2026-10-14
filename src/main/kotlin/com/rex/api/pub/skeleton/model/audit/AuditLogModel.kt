package com.rex.api.pub.skeleton.model.audit

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import java.time.LocalDateTime

/**
 * 감사 로그 Model
 * Controller 레이어에서 사용
 */
data class AuditLogModel(
    @field:JsonProperty("id")
    val id: Long,
    @field:JsonProperty("eventType")
    val eventType: AuditEventType,
    @field:JsonProperty("eventCategory")
    val eventCategory: String,
    @field:JsonProperty("userId")
    val userId: Long?,
    @field:JsonProperty("username")
    val username: String?,
    @field:JsonProperty("ipAddress")
    val ipAddress: String?,
    @field:JsonProperty("userAgent")
    val userAgent: String?,
    @field:JsonProperty("resourceType")
    val resourceType: String?,
    @field:JsonProperty("resourceId")
    val resourceId: String?,
    @field:JsonProperty("action")
    val action: String?,
    @field:JsonProperty("description")
    val description: String?,
    @field:JsonProperty("oldValue")
    val oldValue: String?,
    @field:JsonProperty("newValue")
    val newValue: String?,
    @field:JsonProperty("result")
    val result: AuditResult,
    @field:JsonProperty("failureReason")
    val failureReason: String?,
    @field:JsonProperty("sessionId")
    val sessionId: String?,
    @field:JsonProperty("clientId")
    val clientId: String?,
    @field:JsonProperty("createdAt")
    val createdAt: LocalDateTime,
)