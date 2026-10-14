package com.rex.api.pub.skeleton.service.audit

import com.rex.api.pub.skeleton.dto.audit.AuditLogDto
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.audit.AuditLogEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * 감사 로그 비즈니스 서비스
 * 감사 로그 기록 비즈니스 로직 담당
 *
 * **모든 감사 기록은 호출자 스레드에서 동기로, 호출자의 트랜잭션에 참여해** 남는다.
 * [log] 는 실패를 전파한다(보안 실패 이벤트 — 기록이 안 되면 업무도 실패해야 한다), [logSafely] 는 실패를 잡아 로그로만 남긴다
 * (성공·부수 이벤트). 실패 경로의 감사가 살아남는 것은 호출자의 `noRollbackFor`(`AuthBizService.login`·`SocialAuthBizService.socialLogin`) 덕이다.
 * 비동기 감사가 필요하면 `@Async` 메서드를 **별도 빈**으로 나눈다(`.claude/rules/conventions.md` 「데이터베이스」 3) —
 * 같은 클래스 안의 `@Async` 는 프록시를 지나지 않아 동기로 돈다.
 */
@Service
class AuditBizService(
    private val auditLogEntityService: AuditLogEntityService,
    private val objectMapper: JsonMapper,
) {
    private val logger = logger()

    companion object {
        const val CATEGORY_AUTH = "AUTH"
        const val CATEGORY_ACCOUNT = "ACCOUNT"
        const val CATEGORY_PERMISSION = "PERMISSION"
        const val CATEGORY_OAUTH2 = "OAUTH2"
        const val CATEGORY_ADMIN = "ADMIN"
        const val CATEGORY_SECURITY = "SECURITY"
    }

    /**
     * 감사 로그 기록 — 실패를 잡아 로그로만 남긴다 (성공·부수 이벤트용)
     */
    fun logSafely(
        eventType: AuditEventType,
        eventCategory: String,
        userId: Long? = null,
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        resourceType: String? = null,
        resourceId: String? = null,
        action: String? = null,
        description: String? = null,
        oldValue: Any? = null,
        newValue: Any? = null,
        result: AuditResult = AuditResult.SUCCESS,
        failureReason: String? = null,
        sessionId: String? = null,
        clientId: String? = null,
    ) {
        try {
            doLog(
                eventType, eventCategory, userId, username, ipAddress, userAgent,
                resourceType, resourceId, action, description, oldValue, newValue,
                result, failureReason, sessionId, clientId,
            )
        } catch (e: Exception) {
            logger.error("감사 로그 기록 실패: ${e.message}", e)
        }
    }

    /**
     * 감사 로그 기록 — 실패를 전파한다 (보안 실패 이벤트용)
     */
    fun log(
        eventType: AuditEventType,
        eventCategory: String,
        userId: Long? = null,
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        resourceType: String? = null,
        resourceId: String? = null,
        action: String? = null,
        description: String? = null,
        oldValue: Any? = null,
        newValue: Any? = null,
        result: AuditResult = AuditResult.SUCCESS,
        failureReason: String? = null,
        sessionId: String? = null,
        clientId: String? = null,
    ) {
        doLog(
            eventType, eventCategory, userId, username, ipAddress, userAgent,
            resourceType, resourceId, action, description, oldValue, newValue,
            result, failureReason, sessionId, clientId,
        )
    }

    private fun doLog(
        eventType: AuditEventType,
        eventCategory: String,
        userId: Long?,
        username: String?,
        ipAddress: String?,
        userAgent: String?,
        resourceType: String?,
        resourceId: String?,
        action: String?,
        description: String?,
        oldValue: Any?,
        newValue: Any?,
        result: AuditResult,
        failureReason: String?,
        sessionId: String?,
        clientId: String?,
    ) {
        val auditLog = AuditLogDto(
            eventType = eventType,
            eventCategory = eventCategory,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent?.take(500),
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            description = description,
            oldValue = oldValue?.let { serializeValue(it) },
            newValue = newValue?.let { serializeValue(it) },
            result = result,
            failureReason = failureReason,
            sessionId = sessionId,
            clientId = clientId,
        )

        auditLogEntityService.create(auditLog)
        logger.debug("감사 로그 기록: type=$eventType, user=$username, result=$result")
    }

    private fun serializeValue(value: Any): String = try {
        if (value is String) value else objectMapper.writeValueAsString(value)
    } catch (e: Exception) {
        value.toString()
    }

    // ===== 편의 메서드 =====

    fun logLoginSuccess(
        userId: Long,
        username: String,
        ipAddress: String?,
        userAgent: String?,
        sessionId: String?,
    ) {
        logSafely(
            eventType = AuditEventType.LOGIN_SUCCESS,
            eventCategory = CATEGORY_AUTH,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            description = "로그인 성공",
            sessionId = sessionId,
        )
    }

    fun logLoginFailure(
        username: String,
        ipAddress: String?,
        userAgent: String?,
        reason: String,
    ) {
        log(
            eventType = AuditEventType.LOGIN_FAILURE,
            eventCategory = CATEGORY_AUTH,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            description = "로그인 실패",
            result = AuditResult.FAILURE,
            failureReason = reason,
        )
    }

    fun logLogout(
        userId: Long,
        username: String,
        ipAddress: String?,
        sessionId: String?,
    ) {
        logSafely(
            eventType = AuditEventType.LOGOUT,
            eventCategory = CATEGORY_AUTH,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            description = "로그아웃",
            sessionId = sessionId,
        )
    }

    fun logTokenRevoke(
        userId: Long?,
        username: String?,
        ipAddress: String?,
        clientId: String?,
    ) {
        logSafely(
            eventType = AuditEventType.TOKEN_REVOKE,
            eventCategory = CATEGORY_AUTH,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            clientId = clientId,
            description = "토큰 취소",
        )
    }

    fun logPasswordChange(
        userId: Long,
        username: String,
        ipAddress: String?,
    ) {
        logSafely(
            eventType = AuditEventType.PASSWORD_CHANGE,
            eventCategory = CATEGORY_AUTH,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            description = "비밀번호 변경",
        )
    }

    fun logAccountLocked(
        userId: Long?,
        username: String,
        ipAddress: String?,
        reason: String,
    ) {
        log(
            eventType = AuditEventType.ACCOUNT_LOCKED,
            eventCategory = CATEGORY_SECURITY,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            description = "계정 잠금",
            failureReason = reason,
        )
    }

    fun logSuspiciousActivity(
        userId: Long?,
        username: String?,
        ipAddress: String?,
        description: String,
    ) {
        log(
            eventType = AuditEventType.SUSPICIOUS_ACTIVITY,
            eventCategory = CATEGORY_SECURITY,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            description = description,
            result = AuditResult.BLOCKED,
        )
    }

    fun logIpBlocked(ipAddress: String, reason: String) {
        log(
            eventType = AuditEventType.IP_BLOCKED,
            eventCategory = CATEGORY_SECURITY,
            ipAddress = ipAddress,
            description = "IP 차단",
            failureReason = reason,
            result = AuditResult.BLOCKED,
        )
    }

    fun logBruteForceDetected(
        username: String?,
        ipAddress: String?,
    ) {
        log(
            eventType = AuditEventType.BRUTE_FORCE_DETECTED,
            eventCategory = CATEGORY_SECURITY,
            username = username,
            ipAddress = ipAddress,
            description = "Brute Force 공격 감지",
            result = AuditResult.BLOCKED,
        )
    }

    // ===== 소셜 로그인 관련 =====

    fun logSocialLoginSuccess(
        userId: Long,
        username: String,
        provider: String,
        ipAddress: String?,
        userAgent: String?,
        sessionId: String?,
    ) {
        logSafely(
            eventType = AuditEventType.SOCIAL_LOGIN_SUCCESS,
            eventCategory = CATEGORY_AUTH,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            resourceType = "SOCIAL_PROVIDER",
            resourceId = provider,
            description = "$provider 소셜 로그인 성공",
            sessionId = sessionId,
        )
    }

    fun logSocialLoginFailure(
        provider: String,
        providerId: String?,
        ipAddress: String?,
        userAgent: String?,
        reason: String,
    ) {
        log(
            eventType = AuditEventType.SOCIAL_LOGIN_FAILURE,
            eventCategory = CATEGORY_AUTH,
            ipAddress = ipAddress,
            userAgent = userAgent,
            resourceType = "SOCIAL_PROVIDER",
            resourceId = provider,
            description = "$provider 소셜 로그인 실패 (providerId: $providerId)",
            result = AuditResult.FAILURE,
            failureReason = reason,
        )
    }

    fun logSocialAccountLinked(
        userId: Long,
        username: String,
        provider: String,
        providerId: String,
        ipAddress: String?,
    ) {
        logSafely(
            eventType = AuditEventType.SOCIAL_ACCOUNT_LINKED,
            eventCategory = CATEGORY_ACCOUNT,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            resourceType = "SOCIAL_ACCOUNT",
            resourceId = "$provider:$providerId",
            description = "$provider 소셜 계정 연동",
        )
    }

    fun logSocialAccountUnlinked(
        userId: Long,
        username: String,
        provider: String,
        ipAddress: String?,
    ) {
        logSafely(
            eventType = AuditEventType.SOCIAL_ACCOUNT_UNLINKED,
            eventCategory = CATEGORY_ACCOUNT,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            resourceType = "SOCIAL_PROVIDER",
            resourceId = provider,
            description = "$provider 소셜 계정 연동 해제",
        )
    }

    // ===== 조회 메서드 =====

    fun getAuditLogs(pageable: Pageable): Page<AuditLogDto> =
        auditLogEntityService.getAll(pageable)

    fun getAuditLogsByUser(userId: Long, pageable: Pageable): Page<AuditLogDto> =
        auditLogEntityService.getByUserId(userId, pageable)

    fun getAuditLogsByEventType(eventType: AuditEventType, pageable: Pageable): Page<AuditLogDto> =
        auditLogEntityService.getByEventType(eventType, pageable)

    fun searchAuditLogs(
        userId: Long?,
        eventType: AuditEventType?,
        eventCategory: String?,
        result: AuditResult?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogDto> = auditLogEntityService.search(
        userId,
        eventType,
        eventCategory,
        result,
        startDate,
        endDate,
        pageable,
    )

    fun countLoginFailuresByIp(ipAddress: String, since: LocalDateTime): Long =
        auditLogEntityService.countLoginFailuresByIp(ipAddress, since)

    fun countLoginFailuresByUsername(username: String, since: LocalDateTime): Long =
        auditLogEntityService.countLoginFailuresByUsername(username, since)
}