package com.rex.api.pub.skeleton.service.user

import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.notification.MailMessage
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.notification.MailSender
import com.rex.api.pub.skeleton.util.TokenHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

/**
 * 비밀번호 재설정·이메일 인증 토큰이 [MailSender] 로만 나가는 계약.
 *
 * M-3 재설정 요청 → `send` 1회, 수신자 = 이메일, 본문에 원본 토큰 · M-4 없는 이메일 → 발송 없음 ·
 * M-5 인증 요청 → 같은 단언 · M-6 `send` 예외는 두 경로 모두 그대로 전파(트랜잭션 롤백은 통합 `MailSendFailureIntegrationTest`).
 */
class UserSelfBizServiceMailTest : BehaviorSpec({
    class Fixture {
        val userEntityService = mockk<UserEntityService>()
        val mailSender = mockk<MailSender>(relaxUnitFun = true)
        val tokenHasher = mockk<TokenHasher>()
        val service =
            UserSelfBizService(
                userEntityService = userEntityService,
                userSessionEntityService = mockk<UserSessionEntityService>(relaxed = true),
                tokenEntityService = mockk<TokenEntityService>(relaxed = true),
                sessionCacheBizService = mockk<SessionCacheBizService>(relaxed = true),
                passwordEncoder = mockk<PasswordEncoder>(relaxed = true),
                auditBizService = mockk<AuditBizService>(relaxed = true),
                tokenHasher = tokenHasher,
                mailSender = mailSender,
                userSessionRevocationBizService = mockk(relaxed = true),
            )
        val user =
            UserDto(
                id = 7,
                loginId = "mail_user",
                passwordHash = "n/a",
                email = "u@x.io",
                phone = null,
                name = null,
                nickname = null,
                profileImage = null,
                status = UserStatus.ACTIVE,
                lastLoginAt = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        init {
            every { tokenHasher.generateToken() } returns TokenHasher.TokenPair(rawToken = "RAW-1", hashedToken = "HASH-1")
            every { userEntityService.getUserByEmail("u@x.io") } returns user
            every { userEntityService.getUserById(7) } returns user
        }
    }

    Given("등록된 이메일로 비밀번호 재설정을 요청하면") {
        val f = Fixture()
        f.service.requestPasswordReset("u@x.io")

        Then("M-3 메일이 한 통 나가고 수신자·본문(원본 토큰)이 맞다") {
            val sent = slot<MailMessage>()
            verify(exactly = 1) { f.mailSender.send(capture(sent)) }
            sent.captured.to shouldBe "u@x.io"
            sent.captured.subject shouldBe UserSelfBizService.PASSWORD_RESET_MAIL_SUBJECT
            sent.captured.body.contains("RAW-1") shouldBe true
        }
    }

    Given("등록되지 않은 이메일로 재설정을 요청하면") {
        val f = Fixture()
        every { f.userEntityService.getUserByEmail("nobody@x.io") } returns null
        f.service.requestPasswordReset("nobody@x.io")

        Then("M-4 메일은 나가지 않는다 (존재 여부 비노출)") {
            verify(exactly = 0) { f.mailSender.send(any()) }
        }
    }

    Given("이메일 인증을 요청하면") {
        val f = Fixture()
        f.service.requestEmailVerification(7, "u@x.io")

        Then("M-5 인증 메일이 한 통 나가고 본문에 원본 토큰이 있다") {
            val sent = slot<MailMessage>()
            verify(exactly = 1) { f.mailSender.send(capture(sent)) }
            sent.captured.to shouldBe "u@x.io"
            sent.captured.subject shouldBe UserSelfBizService.EMAIL_VERIFICATION_MAIL_SUBJECT
            sent.captured.body.contains("RAW-1") shouldBe true
        }
    }

    Given("메일 발송이 실패하면") {
        val f = Fixture()
        every { f.mailSender.send(any()) } throws IllegalStateException("smtp down")

        Then("M-6 재설정 요청은 예외를 그대로 전파한다") {
            shouldThrow<IllegalStateException> { f.service.requestPasswordReset("u@x.io") }
        }

        Then("M-6 이메일 인증 요청도 예외를 그대로 전파한다") {
            shouldThrow<IllegalStateException> { f.service.requestEmailVerification(7, "u@x.io") }
        }
    }
})