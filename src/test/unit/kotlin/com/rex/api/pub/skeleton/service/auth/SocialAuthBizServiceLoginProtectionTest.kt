package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.client.social.SocialOAuth2Client
import com.rex.api.pub.skeleton.dto.auth.IssuedSessionDto
import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserSocialAccountDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSocialAccountEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime

/**
 * 소셜 로그인도 로그인 보호(차단 IP·계정 잠금)를 집행한다 — 예전에는 잠긴 계정이 소셜로는 그대로 로그인됐다.
 *
 * 소셜 검증 실패는 비밀번호 추측이 아니므로 실패 카운터를 올리지 않는다(SP-3).
 */
class SocialAuthBizServiceLoginProtectionTest : BehaviorSpec({
    val client = mockk<SocialOAuth2Client>()
    val userEntityService = mockk<UserEntityService>(relaxUnitFun = true)
    val userSocialAccountEntityService = mockk<UserSocialAccountEntityService>(relaxUnitFun = true)
    val sessionIssueBizService = mockk<SessionIssueBizService>()
    val loginAttemptBizService = mockk<LoginAttemptBizService>(relaxUnitFun = true)
    val auditBizService = mockk<AuditBizService>(relaxUnitFun = true)
    val service =
        SocialAuthBizService(listOf(client), userEntityService, userSocialAccountEntityService, sessionIssueBizService, loginAttemptBizService, auditBizService)

    val ip = "203.0.113.10"
    val now = LocalDateTime.now()
    val user =
        UserDto(
            id = 7L, loginId = "user1", passwordHash = null, email = null, phone = null, name = "이름", nickname = "별명", profileImage = "p",
            status = UserStatus.ACTIVE, lastLoginAt = null, createdAt = now, updatedAt = now,
        )

    beforeContainer {
        clearAllMocks()
        every { client.provider } returns SocialProvider.GOOGLE
        every { client.getAccessToken("code", null) } returns OAuth2TokenResponse(accessToken = "provider-token")
        every { client.getUserInfo("provider-token") } returns
            SocialUserInfo(provider = SocialProvider.GOOGLE, providerId = "g-1", email = null, name = null, profileImage = null)
        every { userSocialAccountEntityService.getByProviderAndProviderId("GOOGLE", "g-1") } returns
            UserSocialAccountDto(
                id = 1L, userId = 7L, provider = "GOOGLE", providerId = "g-1", email = null, name = null, profileImage = null,
                accessToken = null, refreshToken = null, tokenExpiresAt = null, createdAt = now, updatedAt = now,
            )
        every { userEntityService.getUserById(7L) } returns user
        every { loginAttemptBizService.isIpBlocked(any()) } returns false
        every { loginAttemptBizService.isUserLocked(any()) } returns false
        every { loginAttemptBizService.accountLockedException(any()) } answers {
            AccountLockedException("계정이 잠겼습니다. ${loginAttemptBizService.getRemainingLockTime(firstArg())}초 후 다시 시도하세요")
        }
        every { sessionIssueBizService.issue(user, any(), any(), any()) } returns
            IssuedSessionDto("session-1", TokenResponseDto(accessToken = "a", refreshToken = "r", expiresIn = 3600L))
    }

    Given("SP-1 차단된 IP") {
        every { loginAttemptBizService.isIpBlocked(ip) } returns true

        Then("ForbiddenException — 세션을 만들지 않고 실패 감사를 남긴다") {
            shouldThrow<ForbiddenException> { service.socialLogin(SocialProvider.GOOGLE, "code", null, "WEB", "agent", ip) }
            verify(exactly = 0) { sessionIssueBizService.issue(any(), any(), any(), any()) }
            verify(exactly = 1) { auditBizService.logSocialLoginFailure("GOOGLE", "g-1", ip, "agent", "차단된 IP") }
        }
    }

    Given("SP-2 잠긴 계정") {
        every { loginAttemptBizService.isUserLocked("user1") } returns true
        every { loginAttemptBizService.getRemainingLockTime("user1") } returns 600L

        Then("AccountLockedException — 세션을 만들지 않는다") {
            shouldThrow<AccountLockedException> { service.socialLogin(SocialProvider.GOOGLE, "code", null, "WEB", "agent", ip) }
            verify(exactly = 0) { sessionIssueBizService.issue(any(), any(), any(), any()) }
            verify(exactly = 1) { auditBizService.logSocialLoginFailure("GOOGLE", "g-1", ip, "agent", "잠긴 계정") }
        }
    }

    Given("SP-3 잠기지 않은 계정") {
        Then("세션을 발급한다 — 로그인 실패 카운터는 건드리지 않는다") {
            service.socialLogin(SocialProvider.GOOGLE, "code", null, "WEB", "agent", ip).accessToken shouldBe "a"
            verify(exactly = 0) { loginAttemptBizService.recordFailedAttempt(any(), any()) }
            verify(exactly = 0) { loginAttemptBizService.clearAttempts(any(), any()) }
        }
    }

    Given("SP-4 IP 를 모르는 요청") {
        Then("IP 차단 조회 없이 잠금만 본다") {
            service.socialLogin(SocialProvider.GOOGLE, "code", null, "WEB", "agent", null)
            verify(exactly = 0) { loginAttemptBizService.isIpBlocked(any()) }
            verify(exactly = 1) { loginAttemptBizService.isUserLocked("user1") }
        }
    }
})