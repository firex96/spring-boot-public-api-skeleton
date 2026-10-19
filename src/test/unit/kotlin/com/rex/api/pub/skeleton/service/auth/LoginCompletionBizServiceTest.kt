package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.IssuedSessionDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime

/**
 * 인증 완료 지점 — 세션을 발급하고 성공을 기록한다. 실패 카운터는 요청받을 때만 지우고, 감사 종류는 소셜(provider) 여부로 고른다.
 */
class LoginCompletionBizServiceTest : BehaviorSpec({
    val sessionIssueBizService = mockk<SessionIssueBizService>()
    val loginAttemptBizService = mockk<LoginAttemptBizService>(relaxUnitFun = true)
    val auditBizService = mockk<AuditBizService>(relaxUnitFun = true)
    val service = LoginCompletionBizService(sessionIssueBizService, loginAttemptBizService, auditBizService)

    val now = LocalDateTime.now()
    val user =
        UserDto(
            id = 7L, loginId = "user1", passwordHash = "hash", email = null, phone = null, name = null, nickname = null,
            profileImage = null, status = UserStatus.ACTIVE, lastLoginAt = null, createdAt = now, updatedAt = now,
        )
    val issued = IssuedSessionDto("session-1", TokenResponseDto(accessToken = "a", refreshToken = "r", expiresIn = 3600L))

    beforeContainer {
        clearAllMocks()
        every { sessionIssueBizService.issue(user, "WEB", "agent", "203.0.113.10") } returns issued
    }

    Given("LC-1 비밀번호 출처(카운터 정리 요청)") {
        Then("카운터를 지우고 세션을 발급하고 LOGIN_SUCCESS 를 남긴다") {
            service.complete(user, provider = null, "WEB", "agent", "203.0.113.10", clearAttempts = true) shouldBe issued
            verify(exactly = 1) { loginAttemptBizService.clearAttempts("user1", "203.0.113.10") }
            verify(exactly = 1) { auditBizService.logLoginSuccess(7L, "user1", "203.0.113.10", "agent", "session-1") }
            verify(exactly = 0) { auditBizService.logSocialLoginSuccess(any(), any(), any(), any(), any(), any()) }
        }
    }

    Given("LC-2 소셜 출처(카운터 정리 안 함)") {
        Then("카운터는 그대로 두고 SOCIAL_LOGIN_SUCCESS(provider) 를 남긴다") {
            service.complete(user, SocialProvider.GOOGLE, "WEB", "agent", "203.0.113.10", clearAttempts = false) shouldBe issued
            verify(exactly = 0) { loginAttemptBizService.clearAttempts(any(), any()) }
            verify(exactly = 1) { auditBizService.logSocialLoginSuccess(7L, "user1", "GOOGLE", "203.0.113.10", "agent", "session-1") }
            verify(exactly = 0) { auditBizService.logLoginSuccess(any(), any(), any(), any(), any()) }
        }
    }
})