package com.rex.api.pub.skeleton.persistence.jpa.entity.auth.audit

import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
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
 * 감사 로그 엔티티
 * 시스템 내 주요 이벤트 및 행위를 추적합니다.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = [
        Index(name = "IX_audit_logs_user_id", columnList = "user_id"),
        Index(name = "IX_audit_logs_event_type", columnList = "event_type"),
        Index(name = "IX_audit_logs_created_at", columnList = "created_at"),
        Index(name = "IX_audit_logs_resource_type", columnList = "resource_type"),
    ],
)
class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: AuditEventType,

    @Column(name = "event_category", nullable = false, length = 50)
    var eventCategory: String,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "username", length = 100)
    var username: String? = null,

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null,

    @Column(name = "resource_type", length = 50)
    var resourceType: String? = null,

    @Column(name = "resource_id", length = 100)
    var resourceId: String? = null,

    @Column(name = "action", length = 50)
    var action: String? = null,

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Column(name = "old_value", columnDefinition = "NVARCHAR(MAX)")
    var oldValue: String? = null,

    @Column(name = "new_value", columnDefinition = "NVARCHAR(MAX)")
    var newValue: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    var result: AuditResult = AuditResult.SUCCESS,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "session_id", length = 64)
    var sessionId: String? = null,

    @Column(name = "client_id", length = 64)
    var clientId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)