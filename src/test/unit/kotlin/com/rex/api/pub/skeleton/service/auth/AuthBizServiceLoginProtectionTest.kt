package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.IssuedSessionDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.RoleEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

/**
 * `AuthBizService.login` 의 로그인 보호 집행 — 차단 IP·잠긴 계정 거절, 실패 기록, 성공 시 초기화.
 *
 * 연결이 끊기면(호출 줄 삭제) L-1~L-6 이 실패한다. HTTP 경로(423 `AUTH001`, 관리자 조회·해제)는 e2e `LoginProtectionE2ETest`.
 * IP 차단 경로는 e2e 에서 꺼 두므로(스위트가 자기 IP 를 차단한다) L-1 이 유일한 고정점이다.
 */
class AuthBizServiceLoginProtectionTest : BehaviorSpec({
    val userEntityService = mockk<UserEntityService>(relaxUnitFun = true)
    val userSessionEntityService = mockk<UserSessionEntityService>()
    val userGroupEntityService = mockk<UserGroupEntityService>()
    val roleEntityService = mockk<RoleEntityService>()
    val jwtTokenBizService = mockk<JwtTokenBizService>()
    val sessionCacheBizService = mockk<SessionCacheBizService>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val loginAttemptBizService = mockk<LoginAttemptBizService>(relaxUnitFun = true)
    val sessionIssueBizService = mockk<SessionIssueBizService>()

    val authBizService =
        AuthBizService(
            userEntityService,
            userSessionEntityService,
            userGroupEntityService,
            roleEntityService,
            jwtTokenBizService,
            sessionCacheBizService,
            passwordEncoder,
            loginAttemptBizService,
            sessionIssueBizService,
        )

    val ip = "203.0.113.10"
    val request = LoginRequestDto(loginId = "user1", password = "secret", deviceType = "WEB")
    fun activeUser() =
        UserDto(
            id = 7L,
            loginId = "user1",
            passwordHash = "hash",
            email = null,
            phone = null,
            name = null,
            nickname = null,
            profileImage = null,
            status = UserStatus.ACTIVE,
            lastLoginAt = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

    fun issued() =
        IssuedSessionDto(
            sessionId = "session-1",
            tokens = TokenResponseDto(accessToken = "access-token", refreshToken = "refresh-token", expiresIn = 3600L),
        )

    beforeContainer {
        clearAllMocks()
        every { loginAttemptBizService.isIpBlocked(any()) } returns false
        every { loginAttemptBizService.isUserLocked(any()) } returns false
    }

    Given("L-1 차단된 IP") {
        every { loginAttemptBizService.isIpBlocked(ip) } returns true

        Then("ForbiddenException — 사용자 조회·비밀번호 검증·실패 기록 없이 거절한다") {
            shouldThrow<ForbiddenException> { authBizService.login(request, ip) }
            verify(exactly = 0) { userEntityService.getActiveUserByLoginId(any()) }
            verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
            verify(exactly = 0) { loginAttemptBizService.recordFailedAttempt(any(), any()) }
        }
    }

    Given("L-2 잠긴 계정") {
        every { loginAttemptBizService.isUserLocked("user1") } returns true
        every { loginAttemptBizService.getRemainingLockTime("user1") } returns 900L

        Then("AccountLockedException(남은 초 포함) — 비밀번호를 보지 않는다") {
            val ex = shouldThrow<AccountLockedException> { authBizService.login(request, ip) }
            ex.message shouldContain "900초"
            verify(exactly = 0) { userEntityService.getActiveUserByLoginId(any()) }
            verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        }
    }

    Given("L-3 존재하지 않는 로그인 ID") {
        every { userEntityService.getActiveUserByLoginId("user1") } returns null

        Then("AuthenticationException + 실패 1회 기록(계정 열거 방지 — 존재 여부와 무관하게 같은 경로)") {
            shouldThrow<AuthenticationException> { authBizService.login(request, ip) }
            verify(exactly = 1) { loginAttemptBizService.recordFailedAttempt("user1", ip) }
            verify(exactly = 0) { loginAttemptBizService.clearAttempts(any(), any()) }
        }
    }

    Given("L-4 비밀번호 불일치") {
        every { userEntityService.getActiveUserByLoginId("user1") } returns activeUser()
        every { passwordEncoder.matches("secret", "hash") } returns false

        Then("AuthenticationException + 실패 1회 기록") {
            shouldThrow<AuthenticationException> { authBizService.login(request, ip) }
            verify(exactly = 1) { loginAttemptBizService.recordFailedAttempt("user1", ip) }
            verify(exactly = 0) { loginAttemptBizService.clearAttempts(any(), any()) }
        }
    }

    Given("L-5 비밀번호 일치") {
        val user = activeUser()
        every { userEntityService.getActiveUserByLoginId("user1") } returns user
        every { passwordEncoder.matches("secret", "hash") } returns true
        every { sessionIssueBizService.issue(user, "WEB", null, any()) } returns issued()

        Then("토큰을 돌려주고 사용자 카운터를 지운다 — 실패 기록 없음") {
            val tokens = authBizService.login(request, ip)
            tokens.accessToken shouldBe "access-token"
            tokens.refreshToken shouldBe "refresh-token"
            verify(exactly = 1) { loginAttemptBizService.clearAttempts("user1", ip) }
            verify(exactly = 0) { loginAttemptBizService.recordFailedAttempt(any(), any()) }
        }
    }

    Given("L-6 클라이언트 IP 를 알 수 없는 실패") {
        every { userEntityService.getActiveUserByLoginId("user1") } returns activeUser()
        every { passwordEncoder.matches("secret", "hash") } returns false

        Then("IP 보호는 건너뛰고(isIpBlocked 호출 0) 실패는 ip = null 로 기록한다 — 사용자 카운터는 그대로 동작") {
            shouldThrow<AuthenticationException> { authBizService.login(request, null) }
            verify(exactly = 0) { loginAttemptBizService.isIpBlocked(any()) }
            verify(exactly = 1) { loginAttemptBizService.recordFailedAttempt("user1", null) }
        }
    }

    Given("L-7 클라이언트 IP 를 알 수 없는 성공") {
        val user = activeUser()
        every { userEntityService.getActiveUserByLoginId("user1") } returns user
        every { passwordEncoder.matches("secret", "hash") } returns true
        every { sessionIssueBizService.issue(user, "WEB", null, any()) } returns issued()

        Then("IP 차단 조회 없이 토큰을 돌려주고 사용자 카운터를 지운다") {
            authBizService.login(request, null).accessToken shouldBe "access-token"
            verify(exactly = 0) { loginAttemptBizService.isIpBlocked(any()) }
            verify(exactly = 1) { loginAttemptBizService.clearAttempts("user1", null) }
        }
    }
})