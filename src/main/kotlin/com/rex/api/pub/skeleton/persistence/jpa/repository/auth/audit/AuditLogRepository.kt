package com.rex.api.pub.skeleton.persistence.jpa.repository.auth.audit

import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.audit.AuditLogEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AuditLogRepository : JpaRepository<AuditLogEntity, Long> {

    fun findByUserId(userId: Long, pageable: Pageable): Page<AuditLogEntity>

    fun findByEventType(eventType: AuditEventType, pageable: Pageable): Page<AuditLogEntity>

    fun findByEventCategory(eventCategory: String, pageable: Pageable): Page<AuditLogEntity>

    fun findByIpAddress(ipAddress: String, pageable: Pageable): Page<AuditLogEntity>

    fun findByResourceTypeAndResourceId(
        resourceType: String,
        resourceId: String,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    @Query(
        """
        SELECT a FROM AuditLogEntity a
        WHERE (:userId IS NULL OR a.userId = :userId)
        AND (:eventType IS NULL OR a.eventType = :eventType)
        AND (:eventCategory IS NULL OR a.eventCategory = :eventCategory)
        AND (:result IS NULL OR a.result = :result)
        AND (:startDate IS NULL OR a.createdAt >= :startDate)
        AND (:endDate IS NULL OR a.createdAt <= :endDate)
        ORDER BY a.createdAt DESC
        """,
    )
    fun searchAuditLogs(
        @Param("userId") userId: Long?,
        @Param("eventType") eventType: AuditEventType?,
        @Param("eventCategory") eventCategory: String?,
        @Param("result") result: AuditResult?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    // 특정 IP에서 특정 시간 내 로그인 실패 횟수
    @Query(
        """
        SELECT COUNT(a) FROM AuditLogEntity a
        WHERE a.ipAddress = :ipAddress
        AND a.eventType = 'LOGIN_FAILURE'
        AND a.createdAt >= :since
        """,
    )
    fun countLoginFailuresByIpSince(
        @Param("ipAddress") ipAddress: String,
        @Param("since") since: LocalDateTime,
    ): Long

    // 특정 사용자의 특정 시간 내 로그인 실패 횟수
    @Query(
        """
        SELECT COUNT(a) FROM AuditLogEntity a
        WHERE a.username = :username
        AND a.eventType = 'LOGIN_FAILURE'
        AND a.createdAt >= :since
        """,
    )
    fun countLoginFailuresByUsernameSince(
        @Param("username") username: String,
        @Param("since") since: LocalDateTime,
    ): Long

    // 최근 의심스러운 활동 조회
    fun findByEventTypeInAndCreatedAtAfterOrderByCreatedAtDesc(
        eventTypes: List<AuditEventType>,
        since: LocalDateTime,
    ): List<AuditLogEntity>
}