package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.time.LocalDateTime
import java.util.UUID

/**
 * `email_verified` 의 정본(`TokenEntityService.existsVerifiedEmail`) — 실제 MySQL 에서 파생 쿼리가 `verifiedAt` 으로 판정하는지.
 *
 * 재요청 때의 무효화(`invalidateAllByUserId`)는 `isVerified=true` 를 쓰지만 `verifiedAt` 은 비워 둔다 — 그것을 인증으로 읽으면 안 된다.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class EmailVerifiedQueryIntegrationTest(
    private val tokenEntityService: TokenEntityService,
    private val userEntityService: UserEntityService,
) : BehaviorSpec({
    fun newUserId(): Long =
        userEntityService.createUser(
            loginId = "it_email_${UUID.randomUUID().toString().take(8)}",
            passwordHash = "{noop}unused",
            email = null,
            name = null,
            nickname = null,
            phone = null,
        ).id

    fun issue(userId: Long, email: String) =
        tokenEntityService.createEmailVerificationToken(userId, email, "hash-${UUID.randomUUID()}", LocalDateTime.now().plusHours(1))

    Given("EV-1 링크로 인증한 이메일") {
        Then("그 이메일이면 true, 다른 이메일이면 false") {
            val userId = newUserId()
            tokenEntityService.markEmailVerificationTokenVerified(issue(userId, "a@example.test").id)

            tokenEntityService.existsVerifiedEmail(userId, "a@example.test") shouldBe true
            tokenEntityService.existsVerifiedEmail(userId, "b@example.test") shouldBe false
        }
    }

    Given("EV-2 재요청으로 무효화된 토큰(isVerified=true, verifiedAt=null)") {
        Then("false") {
            val userId = newUserId()
            issue(userId, "c@example.test")
            tokenEntityService.invalidateAllEmailVerificationTokensByUserId(userId)

            tokenEntityService.existsVerifiedEmail(userId, "c@example.test") shouldBe false
        }
    }

    Given("EV-3 이메일 없음") {
        Then("false") {
            tokenEntityService.existsVerifiedEmail(newUserId(), null) shouldBe false
        }
    }
})