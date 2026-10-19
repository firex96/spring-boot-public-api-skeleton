package com.rex.api.pub.skeleton.service.user

import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.auth.UserSessionRevocationBizService
import com.rex.api.pub.skeleton.service.notification.MailSender
import com.rex.api.pub.skeleton.util.TokenHasher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

/**
 * `UserSelfBizService.changePassword` — 성공하면 현재 세션을 뺀 나머지 세션을 폐기하고 그 캐시를 지운다. 실패하면 아무것도 폐기하지 않는다.
 */
class UserSelfBizServicePasswordChangeTest : BehaviorSpec({
    class Fixture {
        val userEntityService = mockk<UserEntityService>(relaxUnitFun = true)
        val userSessionEntityService = mockk<UserSessionEntityService>(relaxUnitFun = true)
        val sessionCacheBizService = mockk<SessionCacheBizService>(relaxUnitFun = true)
        val userSessionRevocationBizService = mockk<UserSessionRevocationBizService>(relaxUnitFun = true)
        val passwordEncoder = mockk<PasswordEncoder>()
        val service =
            UserSelfBizService(
                userEntityService = userEntityService,
                userSessionEntityService = userSessionEntityService,
                tokenEntityService = mockk<TokenEntityService>(relaxed = true),
                sessionCacheBizService = sessionCacheBizService,
                passwordEncoder = passwordEncoder,
                auditBizService = mockk<AuditBizService>(relaxed = true),
                tokenHasher = mockk<TokenHasher>(relaxed = true),
                mailSender = mockk<MailSender>(relaxed = true),
                userSessionRevocationBizService = userSessionRevocationBizService,
            )

        init {
            every { userEntityService.getUserById(7L) } returns
                UserDto(
                    id = 7L, loginId = "user1", passwordHash = "hash", email = null, phone = null, name = null, nickname = null,
                    profileImage = null, status = UserStatus.ACTIVE, lastLoginAt = null, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
                )
            every { passwordEncoder.encode(any()) } returns "new-hash"
            every { userSessionEntityService.getUnrevokedByUserId(7L) } returns listOf(session("current"), session("phone"), session("laptop"))
        }
    }

    Given("PC-1 현재 비밀번호가 맞다") {
        val f = Fixture()
        every { f.passwordEncoder.matches("old", "hash") } returns true

        Then("true — 현재 세션만 남기고 나머지를 PASSWORD_CHANGED 로 폐기한다(규칙은 UserSessionRevocationBizService)") {
            f.service.changePassword(7L, PasswordChangeDto("old", "NewPassword1!"), "203.0.113.10", "current") shouldBe true

            verify(exactly = 1) { f.userSessionRevocationBizService.revokeAll(7L, UserSelfBizService.PASSWORD_CHANGED_REASON, "current") }
        }
    }

    Given("PC-2 현재 비밀번호가 틀리다") {
        val f = Fixture()
        every { f.passwordEncoder.matches("wrong", "hash") } returns false

        Then("false — 비밀번호도 세션도 바꾸지 않는다") {
            f.service.changePassword(7L, PasswordChangeDto("wrong", "NewPassword1!"), null, "current") shouldBe false

            verify(exactly = 0) { f.userEntityService.updatePassword(any(), any()) }
            verify(exactly = 0) { f.userSessionRevocationBizService.revokeAll(any(), any(), any()) }
        }
    }
})

private fun session(sessionId: String) =
    LocalDateTime.now().let { now ->
        UserSessionRecordDto(
            id = 1, userId = 7, sessionId = sessionId, deviceType = null, deviceInfo = null, ipAddress = null, refreshTokenHash = "h",
            accessTokenIssuedAt = now, refreshTokenIssuedAt = now, accessTokenExpiresAt = now, refreshTokenExpiresAt = now,
            lastActivityAt = now, isRevoked = false, revokedAt = null, revokedReason = null, createdAt = now,
        )
    }