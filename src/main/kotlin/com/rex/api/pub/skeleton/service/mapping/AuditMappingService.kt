package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.mapper.AuditMapper
import com.rex.api.pub.skeleton.model.audit.AuditLogModel
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 감사 로그 매핑 서비스
 * DTO ↔ Model 변환 및 BizService 호출 담당
 */
@Service
class AuditMappingService(
    private val auditBizService: AuditBizService,
    private val mapper: AuditMapper,
) {
    fun getAuditLogs(pageable: Pageable): Page<AuditLogModel> =
        auditBizService.getAuditLogs(pageable).map { mapper.toModel(it) }

    fun searchAuditLogs(
        userId: Long?,
        eventType: AuditEventType?,
        eventCategory: String?,
        result: AuditResult?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogModel> = auditBizService.searchAuditLogs(
        userId = userId,
        eventType = eventType,
        eventCategory = eventCategory,
        result = result,
        startDate = startDate,
        endDate = endDate,
        pageable = pageable,
    ).map { mapper.toModel(it) }

    fun getAuditLogsByUser(userId: Long, pageable: Pageable): Page<AuditLogModel> =
        auditBizService.getAuditLogsByUser(userId, pageable).map { mapper.toModel(it) }

    fun getAuditLogsByEventType(eventType: AuditEventType, pageable: Pageable): Page<AuditLogModel> =
        auditBizService.getAuditLogsByEventType(eventType, pageable).map { mapper.toModel(it) }

    fun getEventTypes(): List<String> = AuditEventType.entries.map { it.name }

    fun getEventCategories(): List<String> = listOf(
        AuditBizService.CATEGORY_AUTH,
        AuditBizService.CATEGORY_ACCOUNT,
        AuditBizService.CATEGORY_PERMISSION,
        AuditBizService.CATEGORY_OAUTH2,
        AuditBizService.CATEGORY_ADMIN,
        AuditBizService.CATEGORY_SECURITY,
    )
}