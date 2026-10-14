package com.rex.api.pub.skeleton.dto.audit

import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import java.time.LocalDateTime

/**
 * 감사 로그 DTO
 * Entity ↔ DTO 변환은 AuditMapper에서 담당
 */
data class AuditLogDto(
    val id: Long = 0,
    val eventType: AuditEventType,
    val eventCategory: String,
    val userId: Long? = null,
    val username: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val action: String? = null,
    val description: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val result: AuditResult = AuditResult.SUCCESS,
    val failureReason: String? = null,
    val sessionId: String? = null,
    val clientId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)