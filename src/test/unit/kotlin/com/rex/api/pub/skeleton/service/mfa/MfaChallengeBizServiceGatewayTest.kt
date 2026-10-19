package com.rex.api.pub.skeleton.service.mfa

import com.rex.api.pub.skeleton.config.security.MfaProperties
import com.rex.api.pub.skeleton.dto.auth.IssuedSessionDto
import com.rex.api.pub.skeleton.dto.auth.LoginOutcomeDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.LoginCompletionBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.util.TokenHasher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.LocalDateTime

/**
 * 1단계 출구의 관문(`afterFirstFactor`) — 2차 인증이 켜졌으면 세션 없이 challenge 만, 꺼졌으면 인증 완료.
 * 꺼진 계정의 실패 카운터 정리는 비밀번호 출처에서만(소셜 1단계는 비밀번호 추측과 무관하다).
 */
class MfaChallengeBizServiceGatewayTest : BehaviorSpec({
    val mfaBizService = mockk<MfaBizService>()
    val loginCompletionBizService = mockk<LoginCompletionBizService>()
    val service =
        MfaChallengeBizService(
            redisTemplate = mockk<StringRedisTemplate>(relaxed = true),
            mfaBizService = mfaBizService,
            loginCompletionBizService = loginCompletionBizService,
            loginAttemptBizService = mockk<LoginAttemptBizService>(),
            userEntityService = mockk<UserEntityService>(),
            auditBizService = mockk<AuditBizService>(),
            tokenHasher = TokenHasher(),
            mfaProperties = MfaProperties(secretEncryptionKey = "unused"),
        )

    val now = LocalDateTime.now()
    val user =
        UserDto(
            id = 7L, loginId = "user1", passwordHash = "hash", email = null, phone = null, name = null, nickname = null,
            profileImage = null, status = UserStatus.ACTIVE, lastLoginAt = null, createdAt = now, updatedAt = now,
        )
    val issued = IssuedSessionDto("session-1", TokenResponseDto(accessToken = "a", refreshToken = "r", expiresIn = 3600L))

    beforeContainer {
        clearAllMocks()
        every { loginCompletionBizService.complete(user, any(), any(), any(), any(), any()) } returns issued
        every { mfaBizService.availableMethods(7L) } returns listOf(MfaMethod.TOTP)
    }

    Given("GW-1 2차 인증이 켜진 계정") {
        every { mfaBizService.isEnabledForLogin(7L) } returns true

        Then("challenge 만 만들고 인증 완료(세션 발급)는 부르지 않는다") {
            val outcome = service.afterFirstFactor(user, MfaOrigin.API_PASSWORD, null, "WEB", null, "203.0.113.10")
            outcome.shouldBeInstanceOf<LoginOutcomeDto.MfaRequired>().challenge.expiresIn shouldBe 300L
            verify(exactly = 0) { loginCompletionBizService.complete(any(), any(), any(), any(), any(), any()) }
        }
    }

    Given("GW-2 2차 인증이 꺼진 계정") {
        every { mfaBizService.isEnabledForLogin(7L) } returns false

        Then("비밀번호 출처는 카운터를 지우며 완료, 소셜 출처는 지우지 않고 완료") {
            service.afterFirstFactor(user, MfaOrigin.AUTHORIZE, null, "WEB", null, null).shouldBeInstanceOf<LoginOutcomeDto.Tokens>().session shouldBe issued
            verify(exactly = 1) { loginCompletionBizService.complete(user, null, "WEB", null, null, clearAttempts = true) }

            service.afterFirstFactor(user, MfaOrigin.SOCIAL_BROWSER, SocialProvider.KAKAO, "WEB", null, null)
            verify(exactly = 1) { loginCompletionBizService.complete(user, SocialProvider.KAKAO, "WEB", null, null, clearAttempts = false) }
        }
    }
})