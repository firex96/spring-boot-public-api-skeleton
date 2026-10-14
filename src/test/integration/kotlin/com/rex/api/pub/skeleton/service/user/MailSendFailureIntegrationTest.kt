package com.rex.api.pub.skeleton.service.user

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.EmailVerificationTokenRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.PasswordResetTokenRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserRepository
import com.rex.api.pub.skeleton.service.notification.MailSender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.every
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.util.UUID

/**
 * 메일 발송 실패 계약 — [MailSender.send] 가 던지면 같은 auth 트랜잭션의 토큰 저장이 **롤백**된다(받을 수 없는 토큰을 남기지 않는다).
 *
 * M-7 비밀번호 재설정 · M-8 이메일 인증 — 두 경로 각각.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class MailSendFailureIntegrationTest(
    private val userSelfBizService: UserSelfBizService,
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
) : BehaviorSpec() {
    @MockkBean
    lateinit var mailSender: MailSender

    init {
        val suffix = UUID.randomUUID().toString().take(8)
        val createdUserIds = mutableListOf<Long>()

        fun newUser(tag: String): UserEntity =
            userRepository.save(
                UserEntity(loginId = "mailfail_${tag}_$suffix", passwordHash = "n/a", email = "mailfail_${tag}_$suffix@example.com"),
            ).also { createdUserIds += it.id }

        // @MockkBean 은 테스트마다 초기화된다(MockkClear.AFTER) — 스텁은 beforeTest 에
        beforeTest { every { mailSender.send(any()) } throws IllegalStateException("smtp down") }

        afterSpec {
            createdUserIds.forEach { userId ->
                passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(userId))
                emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByUserIdAndIsVerifiedFalseOrderByCreatedAtDesc(userId))
            }
            userRepository.deleteAllById(createdUserIds)
        }

        Given("메일 발송이 실패하는 환경") {
            When("비밀번호 재설정을 요청하면") {
                Then("M-7 예외가 전파되고 재설정 토큰 행은 남지 않는다") {
                    val user = newUser("reset")

                    shouldThrow<IllegalStateException> { userSelfBizService.requestPasswordReset(user.email!!) }

                    passwordResetTokenRepository.findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(user.id).shouldBeEmpty()
                }
            }

            When("이메일 인증을 요청하면") {
                Then("M-8 예외가 전파되고 인증 토큰 행은 남지 않는다") {
                    val user = newUser("verify")

                    shouldThrow<IllegalStateException> { userSelfBizService.requestEmailVerification(user.id, user.email!!) }

                    emailVerificationTokenRepository.findByUserIdAndIsVerifiedFalseOrderByCreatedAtDesc(user.id).shouldBeEmpty()
                }
            }
        }
    }
}