package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.AccessTokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserInfoDto
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.exception.auth.DuplicateUserException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.exception.auth.InvalidTokenException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.RoleEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/**
 * 인증 비즈니스 — 가입·로그인·로그아웃·토큰 재발급·비밀번호 변경. 리포지토리는 EntityService 뒤에 있고 Entity 는 여기까지 오지 않는다.
 */
@Service
class AuthBizService(
    private val userEntityService: UserEntityService,
    private val userSessionEntityService: UserSessionEntityService,
    private val userGroupEntityService: UserGroupEntityService,
    private val roleEntityService: RoleEntityService,
    private val jwtTokenBizService: JwtTokenBizService,
    private val sessionCacheBizService: SessionCacheBizService,
    private val passwordEncoder: PasswordEncoder,
    private val loginAttemptBizService: LoginAttemptBizService,
) {
    private val log = logger()

    @Transactional(transactionManager = "authTransactionManager")
    fun signup(request: SignupRequestDto): UserInfoDto {
        // 중복 검사
        if (userEntityService.existsByLoginId(request.loginId)) {
            throw DuplicateUserException("이미 사용 중인 로그인 ID입니다: ${request.loginId}")
        }
        request.email?.let {
            if (userEntityService.existsByEmail(it)) {
                throw DuplicateUserException("이미 사용 중인 이메일입니다: $it")
            }
        }

        // 사용자 생성
        val savedUser =
            userEntityService.createUser(
                loginId = request.loginId,
                passwordHash = encodePassword(request.password),
                email = request.email,
                name = request.name,
                nickname = request.nickname,
                phone = request.phone,
            )

        // 기본 그룹 할당
        val defaultGroup = roleEntityService.getDefault()
        if (defaultGroup != null) {
            userGroupEntityService.assign(userId = savedUser.id, groupId = defaultGroup.id, assignedBy = null, expiresAt = null)
        }
        val groups = if (defaultGroup != null) listOf(defaultGroup.code) else emptyList()

        return toUserInfo(savedUser, groups)
    }

    /**
     * 로그인 — 로그인 보호(차단 IP·계정 잠금·실패 기록)를 집행한다.
     *
     * `noRollbackFor`: 인증 실패는 오류가 아니라 업무 결과다. 실패 기록(`recordFailedAttempt`)이 남기는 잠금 감사 로그·IP 자동 차단 행은
     * 이 auth 트랜잭션에 참여하므로(`AuditLogRepository`·`BlockedIpRepository` 는 `authTransactionManager`), 예외로 롤백되면 보호 장치가
     * 기록을 잃는다. 실패 경로에서 이 메서드 자체는 아무것도 쓰지 않으니 커밋해도 잃을 것이 없다.
     */
    @Transactional(
        transactionManager = "authTransactionManager",
        noRollbackFor = [AuthenticationException::class, AccountLockedException::class, ForbiddenException::class],
    )
    fun login(
        request: LoginRequestDto,
        ipAddress: String?,
    ): TokenResponseDto {
        // 로그인 보호 — 차단 IP · 잠긴 계정은 비밀번호를 보기 전에 거절한다.
        // IP 를 모르면(프록시 헤더 불신 등) IP 보호는 건너뛴다 — 공유 버킷에 모으면 임계에서 그 값이 차단돼 IP 없는 로그인이 전부 막힌다
        if (ipAddress != null && loginAttemptBizService.isIpBlocked(ipAddress)) {
            throw ForbiddenException("차단된 IP 입니다")
        }
        if (loginAttemptBizService.isUserLocked(request.loginId)) {
            throw AccountLockedException(
                "계정이 잠겼습니다. ${loginAttemptBizService.getRemainingLockTime(request.loginId)}초 후 다시 시도하세요",
            )
        }

        // 사용자 조회·비밀번호 검증 — 존재하지 않는 ID 도 같은 메시지·같은 기록(계정 열거 방지)
        val user = userEntityService.getActiveUserByLoginId(request.loginId)
        if (user == null || user.passwordHash == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            // 실패 기록 — 임계 도달 시 계정 잠금(Redis)·IP 자동 차단(DB). noRollbackFor 로 예외 뒤에도 커밋된다
            loginAttemptBizService.recordFailedAttempt(request.loginId, ipAddress)
            throw AuthenticationException("아이디 또는 비밀번호가 올바르지 않습니다")
        }
        loginAttemptBizService.clearAttempts(request.loginId, ipAddress)

        return createSessionAndTokens(user, request.deviceType, request.deviceInfo, ipAddress)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun logout(sessionId: String) {
        userSessionEntityService.revoke(sessionId, "LOGOUT")
        sessionCacheBizService.deleteSession(sessionId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun logoutAllSessions(
        userId: Long,
        excludeCurrentSession: String? = null,
    ) {
        if (excludeCurrentSession != null) {
            userSessionEntityService.revokeAllByUserIdExcept(userId, excludeCurrentSession, "LOGOUT_ALL")
        } else {
            userSessionEntityService.revokeAllByUserId(userId, "LOGOUT_ALL")
        }
        sessionCacheBizService.deleteAllUserSessions(userId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun refresh(refreshToken: String): AccessTokenResponseDto {
        val payload =
            jwtTokenBizService.validateToken(refreshToken)
                ?: throw InvalidTokenException("유효하지 않은 리프레시 토큰입니다")
        if (!payload.isRefreshToken) {
            throw InvalidTokenException("리프레시 토큰이 아닙니다")
        }

        val session =
            userSessionEntityService.getUnrevokedBySessionId(payload.sessionId)
                ?: throw InvalidTokenException("세션이 존재하지 않거나 만료되었습니다")
        if (session.refreshTokenExpiresAt.isBefore(LocalDateTime.now())) {
            throw InvalidTokenException("리프레시 토큰이 만료되었습니다")
        }

        val groups = userGroupEntityService.getActiveGroupCodesByUserId(session.userId)
        val now = LocalDateTime.now()
        val accessTokenExpiration = now.plusSeconds(jwtTokenBizService.getAccessTokenExpirationSeconds())
        val newAccessToken =
            jwtTokenBizService.generateAccessToken(
                userId = session.userId,
                sessionId = session.sessionId,
                groups = groups,
            )

        userSessionEntityService.refreshAccessToken(session.sessionId, issuedAt = now, expiresAt = accessTokenExpiration)
        sessionCacheBizService.deleteSession(session.sessionId)

        return AccessTokenResponseDto(
            accessToken = newAccessToken,
            expiresIn = jwtTokenBizService.getAccessTokenExpirationSeconds(),
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun changePassword(
        userId: Long,
        request: ChangePasswordRequestDto,
    ) {
        val user =
            userEntityService.getUserById(userId)
                ?: throw AuthenticationException("사용자를 찾을 수 없습니다")
        if (user.passwordHash == null || !passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw AuthenticationException("현재 비밀번호가 올바르지 않습니다")
        }

        userEntityService.updatePassword(userId, encodePassword(request.newPassword))

        // 비밀번호 변경 시 모든 세션 무효화
        userSessionEntityService.revokeAllByUserId(userId, "PASSWORD_CHANGED")
        sessionCacheBizService.deleteAllUserSessions(userId)
    }

    fun validateSession(sessionId: String): SessionInfo? = sessionCacheBizService.getSession(sessionId)

    fun getCurrentUser(userId: Long): UserInfoDto {
        val user =
            userEntityService.getUserById(userId)
                ?: throw AuthenticationException("사용자를 찾을 수 없습니다")
        return toUserInfo(user, userGroupEntityService.getActiveGroupCodesByUserId(userId))
    }

    /** 소셜 로그인 등 외부에서 토큰을 만든 뒤 세션 행·캐시만 만들 때 쓴다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun createSession(
        userId: Long,
        sessionId: String,
        refreshToken: String,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
        accessTokenExpiration: LocalDateTime,
        refreshTokenExpiration: LocalDateTime,
    ) {
        val now = LocalDateTime.now()
        val groups = userGroupEntityService.getActiveGroupCodesByUserId(userId)
        persistSession(userId, sessionId, refreshToken, deviceType, deviceInfo, ipAddress, now, accessTokenExpiration, refreshTokenExpiration)
        sessionCacheBizService.cacheSession(
            SessionInfo(
                userId = userId,
                sessionId = sessionId,
                groups = groups,
                permissions = emptyList(),
                deviceType = deviceType,
                ipAddress = ipAddress,
                issuedAt = now.atZone(ZoneId.systemDefault()).toInstant(),
                expiresAt = accessTokenExpiration.atZone(ZoneId.systemDefault()).toInstant(),
            ),
        )
    }

    private fun createSessionAndTokens(
        user: UserDto,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): TokenResponseDto {
        val sessionId = UUID.randomUUID().toString()
        val groups = userGroupEntityService.getActiveGroupCodesByUserId(user.id)
        val now = LocalDateTime.now()
        val accessTokenExpiration = now.plusSeconds(jwtTokenBizService.getAccessTokenExpirationSeconds())
        val refreshTokenExpiration = now.plusSeconds(jwtTokenBizService.getRefreshTokenExpirationSeconds())

        val accessToken =
            jwtTokenBizService.generateAccessToken(
                userId = user.id,
                sessionId = sessionId,
                groups = groups,
            )
        val refreshToken =
            jwtTokenBizService.generateRefreshToken(
                userId = user.id,
                sessionId = sessionId,
            )

        persistSession(user.id, sessionId, refreshToken, deviceType, deviceInfo, ipAddress, now, accessTokenExpiration, refreshTokenExpiration)
        userEntityService.updateLastLoginAt(user.id, now)

        sessionCacheBizService.cacheSession(
            SessionInfo(
                userId = user.id,
                sessionId = sessionId,
                groups = groups,
                permissions = emptyList(), // 초기에는 빈 리스트, getSession 호출 시 로드됨
                deviceType = deviceType,
                ipAddress = ipAddress,
                issuedAt = now.atZone(ZoneId.systemDefault()).toInstant(),
                expiresAt = accessTokenExpiration.atZone(ZoneId.systemDefault()).toInstant(),
            ),
        )

        return TokenResponseDto(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtTokenBizService.getAccessTokenExpirationSeconds(),
        )
    }

    private fun persistSession(
        userId: Long,
        sessionId: String,
        refreshToken: String,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
        now: LocalDateTime,
        accessTokenExpiration: LocalDateTime,
        refreshTokenExpiration: LocalDateTime,
    ) {
        userSessionEntityService.createSession(
            UserSessionRecordDto(
                id = 0,
                userId = userId,
                sessionId = sessionId,
                deviceType = deviceType,
                deviceInfo = deviceInfo,
                ipAddress = ipAddress,
                refreshTokenHash = hashToken(refreshToken),
                accessTokenIssuedAt = now,
                refreshTokenIssuedAt = now,
                accessTokenExpiresAt = accessTokenExpiration,
                refreshTokenExpiresAt = refreshTokenExpiration,
                lastActivityAt = now,
                isRevoked = false,
                revokedAt = null,
                revokedReason = null,
                createdAt = now,
            ),
        )
    }

    private fun toUserInfo(user: UserDto, groups: List<String>): UserInfoDto =
        UserInfoDto(
            id = user.id,
            loginId = user.loginId,
            email = user.email,
            name = user.name,
            nickname = user.nickname,
            phone = user.phone,
            profileImage = user.profileImage,
            groups = groups,
        )

    private fun encodePassword(raw: String): String =
        passwordEncoder.encode(raw) ?: throw IllegalStateException("PasswordEncoder 가 null 을 반환했다")

    /**
     * SHA-256으로 토큰을 해싱합니다.
     *
     * BCrypt는 72바이트 제한이 있어 JWT 토큰에 적합하지 않습니다.
     * Refresh token 저장에는 SHA-256을 사용합니다.
     */
    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}