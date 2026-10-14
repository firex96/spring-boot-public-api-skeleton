package com.rex.api.pub.skeleton.persistence.jpa.service.auth.audit

import com.rex.api.pub.skeleton.dto.audit.AuditLogDto
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.mapper.AuditMapper
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.audit.AuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 감사 로그 엔티티 서비스
 * Entity ↔ DTO 변환 및 Repository 호출 담당
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class AuditLogEntityService(
    private val auditLogRepository: AuditLogRepository,
    private val mapper: AuditMapper,
) {
    /**
     * 감사 로그 저장 — 호출자의 auth 트랜잭션에 **참여**한다(`REQUIRED`).
     *
     * 실패 경로 감사(로그인 실패·잠금·차단·소셜 실패)는 호출자가 `noRollbackFor` 로 커밋을 보장한다
     * (`AuthBizService.login`·`SocialAuthBizService.socialLogin`). 감사 기록은 전부 동기로 여기까지 온다 — `AuditBizService.log`·`logSafely` 는
     * 비동기가 아니다(`@Async` 자기 호출 제거, 감사는 「동기·호출자 트랜잭션 참여」 하나의 계약).
     * `REQUIRES_NEW` 는 같은 auth 풀에서 커넥션이 하나 더 필요해(local·통합 풀 1 → 타임아웃) 쓰지 않는다.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun create(dto: AuditLogDto): AuditLogDto =
        auditLogRepository.save(mapper.toEntity(dto)).let { mapper.toDto(it) }

    /**
     * 전체 감사 로그 페이징 조회
     */
    fun getAll(pageable: Pageable): Page<AuditLogDto> =
        auditLogRepository.findAll(pageable).map { mapper.toDto(it) }

    /**
     * 사용자별 감사 로그 조회
     */
    fun getByUserId(userId: Long, pageable: Pageable): Page<AuditLogDto> =
        auditLogRepository.findByUserId(userId, pageable).map { mapper.toDto(it) }

    /**
     * 이벤트 타입별 감사 로그 조회
     */
    fun getByEventType(eventType: AuditEventType, pageable: Pageable): Page<AuditLogDto> =
        auditLogRepository.findByEventType(eventType, pageable).map { mapper.toDto(it) }

    /**
     * 감사 로그 검색
     */
    fun search(
        userId: Long?,
        eventType: AuditEventType?,
        eventCategory: String?,
        result: AuditResult?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogDto> = auditLogRepository.searchAuditLogs(
        userId,
        eventType,
        eventCategory,
        result,
        startDate,
        endDate,
        pageable,
    ).map { mapper.toDto(it) }

    /**
     * IP별 로그인 실패 횟수 조회
     */
    fun countLoginFailuresByIp(ipAddress: String, since: LocalDateTime): Long =
        auditLogRepository.countLoginFailuresByIpSince(ipAddress, since)

    /**
     * 사용자명별 로그인 실패 횟수 조회
     */
    fun countLoginFailuresByUsername(username: String, since: LocalDateTime): Long =
        auditLogRepository.countLoginFailuresByUsernameSince(username, since)
}