package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.dto.user.UserSessionDto
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSessionEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserSessionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * 사용자 세션 엔티티 서비스 — 세션 행의 생성·조회·폐기. 조회는 `UserSessionRecordDto`(마스킹 없음, BizService 용)
 * 또는 `UserSessionDto`(마스킹, 사용자 응답용)로만 나간다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserSessionEntityService(
    private val userSessionRepository: UserSessionRepository,
    private val mapper: AuthMapper,
) {
    /** `id`·폐기 정보·`createdAt` 은 입력에서 무시한다 — 새 행은 항상 살아 있는 세션이다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun createSession(record: UserSessionRecordDto): UserSessionRecordDto {
        val session =
            UserSessionEntity(
                userId = record.userId,
                sessionId = record.sessionId,
                deviceType = record.deviceType,
                deviceInfo = record.deviceInfo,
                ipAddress = record.ipAddress,
                refreshTokenHash = record.refreshTokenHash,
                accessTokenIssuedAt = record.accessTokenIssuedAt,
                refreshTokenIssuedAt = record.refreshTokenIssuedAt,
                accessTokenExpiresAt = record.accessTokenExpiresAt,
                refreshTokenExpiresAt = record.refreshTokenExpiresAt,
                lastActivityAt = record.lastActivityAt,
            )
        return mapper.toUserSessionRecordDto(userSessionRepository.save(session))
    }

    /** 폐기되지 않은 세션 — 만료(refresh 토큰 만료)는 보지 않는다: 만료됐어도 아직 폐기되지 않았으면 폐기 대상이다. */
    fun getUnrevokedBySessionId(sessionId: String): UserSessionRecordDto? =
        userSessionRepository.findBySessionIdAndIsRevokedFalse(sessionId).getOrNull()?.let { mapper.toUserSessionRecordDto(it) }

    fun getById(id: Long): UserSessionRecordDto? =
        userSessionRepository.findByIdOrNull(id)?.let { mapper.toUserSessionRecordDto(it) }

    fun getUnrevokedByUserId(userId: Long): List<UserSessionRecordDto> =
        userSessionRepository.findByUserIdAndIsRevokedFalseOrderByLastActivityAtDesc(userId).map { mapper.toUserSessionRecordDto(it) }

    /** 살아 있는(폐기 안 됨·refresh 미만료) 세션을 사용자 응답용으로 — sessionId 는 매퍼가 마스킹한다. */
    fun getActiveSessionDtosByUserId(userId: Long, currentSessionId: String?): List<UserSessionDto> =
        userSessionRepository.findByUserIdAndIsRevokedFalseOrderByLastActivityAtDesc(userId)
            .filter { it.isActive() }
            .map { mapper.toUserSessionDto(it, currentSessionId) }

    @Transactional(transactionManager = "authTransactionManager")
    fun revoke(sessionId: String, reason: String) {
        userSessionRepository.revokeSession(sessionId = sessionId, revokedAt = LocalDateTime.now(), reason = reason)
    }

    /** 지정한 세션들만 폐기한다(이미 폐기된 것은 건너뛴다). 빈 목록이면 아무것도 하지 않는다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun revokeAll(sessionIds: Collection<String>, reason: String) {
        if (sessionIds.isEmpty()) return
        userSessionRepository.revokeSessions(sessionIds = sessionIds, revokedAt = LocalDateTime.now(), reason = reason)
    }

    /** 액세스 토큰 재발급 — 발급·만료 시각과 마지막 활동 시각을 함께 갱신한다. 폐기된 세션은 건드리지 않는다(멱등). */
    @Transactional(transactionManager = "authTransactionManager")
    fun refreshAccessToken(sessionId: String, issuedAt: LocalDateTime, expiresAt: LocalDateTime) {
        val session = userSessionRepository.findBySessionIdAndIsRevokedFalse(sessionId).getOrNull() ?: return
        session.accessTokenIssuedAt = issuedAt
        session.accessTokenExpiresAt = expiresAt
        session.lastActivityAt = issuedAt
        userSessionRepository.save(session)
    }
}