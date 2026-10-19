package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.AccessTokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserInfoDto
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.exception.auth.DuplicateUserException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.exception.auth.InvalidTokenException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.RoleEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

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
    private val sessionIssueBizService: SessionIssueBizService,
    private val auditBizService: AuditBizService,
    private val userSessionRevocationBizService: UserSessionRevocationBizService,
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
     * 로그인 — 비밀번호를 확인하고([verifyPassword]) 실패 카운터를 지운 뒤 세션을 발급한다.
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
        val user = verifyPassword(request, ipAddress)
        loginAttemptBizService.clearAttempts(request.loginId, ipAddress)
        val issued = sessionIssueBizService.issue(user, request.deviceType, request.deviceInfo, ipAddress)
        auditBizService.logLoginSuccess(user.id, user.loginId, ipAddress, request.deviceInfo, issued.sessionId)
        return issued.tokens
    }

    /**
     * 비밀번호 확인 — 로그인 보호(차단 IP·계정 잠금·실패 기록)를 집행하고 확인된 사용자를 돌려준다.
     *
     * 성공해도 실패 카운터를 지우지 않는다 — 호출자가 인증이 **최종적으로** 끝난 지점에서 지운다. 성공 감사도 호출자가 세션을 만든 뒤 남긴다.
     * 실패 기록·실패 감사는 호출자 트랜잭션에 참여하므로 호출자가 `noRollbackFor` 를 둔다([login] 참고).
     */
    fun verifyPassword(
        request: LoginRequestDto,
        ipAddress: String?,
    ): UserDto {
        // 로그인 보호 — 차단 IP · 잠긴 계정은 비밀번호를 보기 전에 거절한다.
        // IP 를 모르면(프록시 헤더 불신 등) IP 보호는 건너뛴다 — 공유 버킷에 모으면 임계에서 그 값이 차단돼 IP 없는 로그인이 전부 막힌다
        if (ipAddress != null && loginAttemptBizService.isIpBlocked(ipAddress)) {
            auditBizService.logLoginFailure(request.loginId, ipAddress, request.deviceInfo, FAILURE_BLOCKED_IP)
            throw ForbiddenException("차단된 IP 입니다")
        }
        if (loginAttemptBizService.isUserLocked(request.loginId)) {
            auditBizService.logLoginFailure(request.loginId, ipAddress, request.deviceInfo, FAILURE_ACCOUNT_LOCKED)
            throw loginAttemptBizService.accountLockedException(request.loginId)
        }

        // 사용자 조회·비밀번호 검증 — 존재하지 않는 ID 도 같은 메시지·같은 기록(계정 열거 방지)
        val user = userEntityService.getActiveUserByLoginId(request.loginId)
        if (user == null || user.passwordHash == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            // 실패 기록 — 임계 도달 시 계정 잠금(Redis)·IP 자동 차단(DB). 호출자의 noRollbackFor 로 예외 뒤에도 커밋된다
            loginAttemptBizService.recordFailedAttempt(request.loginId, ipAddress)
            auditBizService.logLoginFailure(request.loginId, ipAddress, request.deviceInfo, FAILURE_INVALID_CREDENTIALS)
            throw AuthenticationException("아이디 또는 비밀번호가 올바르지 않습니다")
        }
        return user
    }

    /**
     * 인가 서버 로그인 화면의 로그인 — [login] 을 그대로 지나 IP 차단·잠금·실패 집계·계정 열거 방지를 재사용한다.
     * 발급된 토큰은 돌려주지 않는다. 브라우저 세션은 사용자 세션 ID 만 들고, 로그아웃·강제 종료가 브라우저 세션에도 걸린다.
     */
    @Transactional(
        transactionManager = "authTransactionManager",
        noRollbackFor = [AuthenticationException::class, AccountLockedException::class, ForbiddenException::class],
    )
    fun loginForBrowser(
        request: LoginRequestDto,
        ipAddress: String?,
    ): SessionInfo = resolveBrowserPrincipal(login(request, ipAddress).accessToken)

    @Transactional(transactionManager = "authTransactionManager")
    fun logout(sessionId: String) {
        userSessionEntityService.revoke(sessionId, "LOGOUT")
        sessionCacheBizService.invalidateSession(sessionId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun logoutAllSessions(
        userId: Long,
        excludeCurrentSession: String? = null,
    ) {
        userSessionRevocationBizService.revokeAll(userId, "LOGOUT_ALL", exceptSessionId = excludeCurrentSession)
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
        userSessionRevocationBizService.revokeAll(userId, "PASSWORD_CHANGED", exceptSessionId = null)
    }

    fun validateSession(sessionId: String): SessionInfo? = sessionCacheBizService.getSession(sessionId)

    /**
     * 막 발급된 access 토큰으로 브라우저 세션 principal 을 만든다 — 토큰의 jti(사용자 세션)가 살아 있어야 한다.
     * 브라우저 세션은 이 사용자 세션 ID 를 들고, 요청마다 [validateSession] 으로 폐기 여부를 다시 본다.
     */
    fun resolveBrowserPrincipal(accessToken: String): SessionInfo {
        val payload = jwtTokenBizService.validateToken(accessToken)
        if (payload == null || payload.isRefreshToken) {
            throw InvalidTokenException("유효하지 않은 토큰입니다")
        }
        val session = validateSession(payload.sessionId)
        if (session == null || session.revoked) {
            throw InvalidTokenException("세션이 유효하지 않습니다")
        }
        return session
    }

    fun getCurrentUser(userId: Long): UserInfoDto {
        val user =
            userEntityService.getUserById(userId)
                ?: throw AuthenticationException("사용자를 찾을 수 없습니다")
        return toUserInfo(user, userGroupEntityService.getActiveGroupCodesByUserId(userId))
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

    companion object {
        /** 로그인 실패 감사의 사유 — `audit_logs.failure_reason`. 사용자에게 내보내는 메시지와 별개다(계정 열거 방지). */
        const val FAILURE_BLOCKED_IP = "BLOCKED_IP"
        const val FAILURE_ACCOUNT_LOCKED = "ACCOUNT_LOCKED"
        const val FAILURE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    }
}