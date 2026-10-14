package com.rex.api.pub.skeleton.fixture

import com.rex.api.pub.skeleton.dto.audit.AuditLogDto
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.audit.AuditLogEntity
import java.time.LocalDateTime
import java.util.UUID

/**
 * 감사 로그 관련 테스트 픽스처.
 *
 * 감사 로그 API 테스트에서 사용되는 요청/응답 객체와 엔티티를 생성합니다.
 *
 * 사용 예시:
 * ```kotlin
 * val auditLog = AuditLogFixture.loginSuccessLog()
 * ```
 */
object AuditLogFixture {
    // 기본 감사 로그 정보
    const val DEFAULT_USERNAME = "testuser"
    const val DEFAULT_IP_ADDRESS = "127.0.0.1"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Test Browser)"
    const val DEFAULT_SESSION_ID = "test-session-id"
    const val DEFAULT_CLIENT_ID = "test-client"

    /**
     * 기본 감사 로그 DTO 생성.
     */
    fun auditLogDto(
        id: Long = 1L,
        eventType: AuditEventType = AuditEventType.LOGIN_SUCCESS,
        eventCategory: String = "AUTH",
        userId: Long? = 1L,
        username: String? = DEFAULT_USERNAME,
        ipAddress: String? = DEFAULT_IP_ADDRESS,
        userAgent: String? = DEFAULT_USER_AGENT,
        resourceType: String? = null,
        resourceId: String? = null,
        action: String? = null,
        description: String? = "로그인 성공",
        oldValue: String? = null,
        newValue: String? = null,
        result: AuditResult = AuditResult.SUCCESS,
        failureReason: String? = null,
        sessionId: String? = DEFAULT_SESSION_ID,
        clientId: String? = DEFAULT_CLIENT_ID,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): AuditLogDto =
        AuditLogDto(
            id = id,
            eventType = eventType,
            eventCategory = eventCategory,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            description = description,
            oldValue = oldValue,
            newValue = newValue,
            result = result,
            failureReason = failureReason,
            sessionId = sessionId,
            clientId = clientId,
            createdAt = createdAt,
        )

    /**
     * 감사 로그 엔티티 생성 (DB 저장용).
     */
    fun auditLogEntity(
        id: Long = 0L,
        eventType: AuditEventType = AuditEventType.LOGIN_SUCCESS,
        eventCategory: String = "AUTH",
        userId: Long? = 1L,
        username: String? = DEFAULT_USERNAME,
        ipAddress: String? = DEFAULT_IP_ADDRESS,
        userAgent: String? = DEFAULT_USER_AGENT,
        resourceType: String? = null,
        resourceId: String? = null,
        action: String? = null,
        description: String? = "로그인 성공",
        oldValue: String? = null,
        newValue: String? = null,
        result: AuditResult = AuditResult.SUCCESS,
        failureReason: String? = null,
        sessionId: String? = DEFAULT_SESSION_ID,
        clientId: String? = DEFAULT_CLIENT_ID,
    ): AuditLogEntity =
        AuditLogEntity(
            id = id,
            eventType = eventType,
            eventCategory = eventCategory,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            description = description,
            oldValue = oldValue,
            newValue = newValue,
            result = result,
            failureReason = failureReason,
            sessionId = sessionId,
            clientId = clientId,
        )

    // ===== 인증 관련 감사 로그 =====

    /**
     * 로그인 성공 감사 로그.
     */
    fun loginSuccessLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
        ipAddress: String = DEFAULT_IP_ADDRESS,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.LOGIN_SUCCESS,
            eventCategory = "AUTH",
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            description = "로그인 성공",
            result = AuditResult.SUCCESS,
        )

    /**
     * 로그인 실패 감사 로그.
     */
    fun loginFailureLog(
        username: String = DEFAULT_USERNAME,
        ipAddress: String = DEFAULT_IP_ADDRESS,
        failureReason: String = "잘못된 비밀번호",
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.LOGIN_FAILURE,
            eventCategory = "AUTH",
            userId = null,
            username = username,
            ipAddress = ipAddress,
            description = "로그인 실패",
            result = AuditResult.FAILURE,
            failureReason = failureReason,
        )

    /**
     * 로그아웃 감사 로그.
     */
    fun logoutLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.LOGOUT,
            eventCategory = "AUTH",
            userId = userId,
            username = username,
            description = "로그아웃",
        )

    /**
     * 토큰 갱신 감사 로그.
     */
    fun tokenRefreshLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.TOKEN_REFRESH,
            eventCategory = "AUTH",
            userId = userId,
            username = username,
            description = "토큰 갱신",
        )

    /**
     * 비밀번호 변경 감사 로그.
     */
    fun passwordChangeLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.PASSWORD_CHANGE,
            eventCategory = "AUTH",
            userId = userId,
            username = username,
            description = "비밀번호 변경",
        )

    // ===== 계정 관련 감사 로그 =====

    /**
     * 계정 생성 감사 로그.
     */
    fun accountCreatedLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.ACCOUNT_CREATED,
            eventCategory = "ACCOUNT",
            userId = userId,
            username = username,
            resourceType = "USER",
            resourceId = userId.toString(),
            action = "CREATE",
            description = "계정 생성",
        )

    /**
     * 계정 잠금 감사 로그.
     */
    fun accountLockedLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
        reason: String = "연속 로그인 실패",
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.ACCOUNT_LOCKED,
            eventCategory = "ACCOUNT",
            userId = userId,
            username = username,
            resourceType = "USER",
            resourceId = userId.toString(),
            action = "LOCK",
            description = "계정 잠금: $reason",
        )

    // ===== 권한 관련 감사 로그 =====

    /**
     * 권한 부여 감사 로그.
     */
    fun permissionGrantedLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
        permissionCode: String = "user:read",
        grantedBy: Long = 1L,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.PERMISSION_GRANTED,
            eventCategory = "PERMISSION",
            userId = userId,
            username = username,
            resourceType = "PERMISSION",
            resourceId = permissionCode,
            action = "GRANT",
            description = "권한 부여: $permissionCode (by user $grantedBy)",
        )

    /**
     * 그룹 할당 감사 로그.
     */
    fun groupAssignedLog(
        userId: Long = 1L,
        username: String = DEFAULT_USERNAME,
        groupCode: String = "ADMIN",
        assignedBy: Long = 1L,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.GROUP_ASSIGNED,
            eventCategory = "GROUP",
            userId = userId,
            username = username,
            resourceType = "GROUP",
            resourceId = groupCode,
            action = "ASSIGN",
            description = "그룹 할당: $groupCode (by user $assignedBy)",
        )

    // ===== 보안 이벤트 감사 로그 =====

    /**
     * IP 차단 감사 로그.
     */
    fun ipBlockedLog(
        ipAddress: String = DEFAULT_IP_ADDRESS,
        reason: String = "Brute force 공격 감지",
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.IP_BLOCKED,
            eventCategory = "SECURITY",
            userId = null,
            username = null,
            ipAddress = ipAddress,
            resourceType = "IP",
            resourceId = ipAddress,
            action = "BLOCK",
            description = "IP 차단: $reason",
            result = AuditResult.BLOCKED,
        )

    /**
     * 의심스러운 활동 감사 로그.
     */
    fun suspiciousActivityLog(
        userId: Long? = null,
        ipAddress: String = DEFAULT_IP_ADDRESS,
        description: String = "비정상적인 접근 패턴 감지",
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.SUSPICIOUS_ACTIVITY,
            eventCategory = "SECURITY",
            userId = userId,
            ipAddress = ipAddress,
            description = description,
            result = AuditResult.BLOCKED,
        )

    // ===== OAuth2 관련 감사 로그 =====

    /**
     * OAuth2 클라이언트 생성 감사 로그.
     */
    fun oauth2ClientCreatedLog(
        clientId: String = "new-client-id",
        createdBy: Long = 1L,
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.OAUTH2_CLIENT_CREATED,
            eventCategory = "OAUTH2",
            userId = createdBy,
            resourceType = "OAUTH2_CLIENT",
            resourceId = clientId,
            action = "CREATE",
            description = "OAuth2 클라이언트 생성: $clientId",
        )

    /**
     * OAuth2 토큰 발급 감사 로그.
     */
    fun oauth2TokenIssuedLog(
        userId: Long = 1L,
        clientId: String = DEFAULT_CLIENT_ID,
        grantType: String = "authorization_code",
    ): AuditLogDto =
        auditLogDto(
            eventType = AuditEventType.OAUTH2_TOKEN_ISSUED,
            eventCategory = "OAUTH2",
            userId = userId,
            clientId = clientId,
            resourceType = "TOKEN",
            action = "ISSUE",
            description = "OAuth2 토큰 발급 (grant_type: $grantType)",
        )

    /**
     * 감사 로그 리스트 생성 (페이징 테스트용).
     */
    fun auditLogDtoList(count: Int = 10): List<AuditLogDto> =
        (1..count).map { index ->
            auditLogDto(
                id = index.toLong(),
                eventType = AuditEventType.entries[index % AuditEventType.entries.size],
                userId = (index % 5 + 1).toLong(),
                username = "user$index",
                sessionId = UUID.randomUUID().toString(),
                createdAt = LocalDateTime.now().minusMinutes(index.toLong()),
            )
        }
}