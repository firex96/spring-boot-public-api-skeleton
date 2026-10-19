package com.rex.api.pub.skeleton.service.user

import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.notification.MailSender
import com.rex.api.pub.skeleton.util.TokenHasher
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

/**
 * `UserSelfBizService.revokeSessionBySessionId` 의 인가 계약 — 요청자 소유 세션만 폐기한다.
 *
 * S-1 소유자 일치 → 폐기(`revoke(sessionId, "LOGOUT")`)·캐시 무효화 · S-2 불일치 → [ForbiddenException], 폐기·무효화 없음 ·
 * S-3 없는(이미 폐기된) 세션 → 멱등 · S-4 만료됐지만 폐기되지 않은 세션 → 소유자면 폐기(조회 기준은 폐기 여부만).
 * 세션 행은 EntityService 가 DTO 로만 돌려주므로 폐기는 의도 메서드 호출로 검증한다.
 */
class UserSelfBizServiceSessionTest : BehaviorSpec({
    // Given 마다 새 mock — verify(exactly = 0) 이 다른 Given 의 호출을 세지 않게
    class Fixture {
        val userSessionEntityService = mockk<UserSessionEntityService>(relaxUnitFun = true)
        val sessionCacheBizService = mockk<SessionCacheBizService>(relaxUnitFun = true)
        val service =
            UserSelfBizService(
                userEntityService = mockk<UserEntityService>(relaxed = true),
                userSessionEntityService = userSessionEntityService,
                tokenEntityService = mockk<TokenEntityService>(relaxed = true),
                sessionCacheBizService = sessionCacheBizService,
                passwordEncoder = mockk<PasswordEncoder>(relaxed = true),
                auditBizService = mockk<AuditBizService>(relaxed = true),
                tokenHasher = mockk<TokenHasher>(relaxed = true),
                mailSender = mockk<MailSender>(relaxed = true),
                userSessionRevocationBizService = mockk(relaxed = true),
            )
    }

    fun session(
        ownerId: Long,
        refreshTokenExpiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),
    ) = UserSessionRecordDto(
        id = 10L,
        userId = ownerId,
        sessionId = "s-1",
        deviceType = null,
        deviceInfo = null,
        ipAddress = null,
        refreshTokenHash = "hash",
        accessTokenIssuedAt = LocalDateTime.now(),
        refreshTokenIssuedAt = LocalDateTime.now(),
        accessTokenExpiresAt = LocalDateTime.now().plusHours(1),
        refreshTokenExpiresAt = refreshTokenExpiresAt,
        lastActivityAt = LocalDateTime.now(),
        isRevoked = false,
        revokedAt = null,
        revokedReason = null,
        createdAt = LocalDateTime.now(),
    )

    Given("소유자가 요청자와 같은 세션") {
        val f = Fixture()
        every { f.userSessionEntityService.getUnrevokedBySessionId("s-1") } returns session(ownerId = 1)

        When("revokeSessionBySessionId(1, s-1)") {
            f.service.revokeSessionBySessionId(1, "s-1")

            Then("S-1 LOGOUT 사유로 폐기되고 캐시 무효화가 한 번씩 일어난다") {
                verify(exactly = 1) { f.userSessionEntityService.revoke("s-1", "LOGOUT") }
                verify(exactly = 1) { f.sessionCacheBizService.invalidateSession("s-1") }
            }
        }
    }

    Given("소유자가 다른 사용자인 세션") {
        val f = Fixture()
        every { f.userSessionEntityService.getUnrevokedBySessionId("s-1") } returns session(ownerId = 2)

        When("요청자 1 이 폐기를 시도하면") {
            Then("S-2 ForbiddenException 이고 폐기·무효화는 없다") {
                shouldThrow<ForbiddenException> { f.service.revokeSessionBySessionId(1, "s-1") }
                verify(exactly = 0) { f.userSessionEntityService.revoke(any(), any()) }
                verify(exactly = 0) { f.sessionCacheBizService.invalidateSession(any()) }
            }
        }
    }

    Given("없는(또는 이미 폐기된) 세션") {
        val f = Fixture()
        every { f.userSessionEntityService.getUnrevokedBySessionId("s-1") } returns null

        When("폐기를 시도하면") {
            Then("S-3 예외 없이 끝나고(멱등) 폐기·무효화는 없다") {
                shouldNotThrowAny { f.service.revokeSessionBySessionId(1, "s-1") }
                verify(exactly = 0) { f.userSessionEntityService.revoke(any(), any()) }
                verify(exactly = 0) { f.sessionCacheBizService.invalidateSession(any()) }
            }
        }
    }

    Given("만료됐지만 폐기되지 않은 소유 세션") {
        val f = Fixture()
        every { f.userSessionEntityService.getUnrevokedBySessionId("s-1") } returns
            session(ownerId = 1, refreshTokenExpiresAt = LocalDateTime.now().minusDays(1))

        When("소유자가 폐기하면") {
            f.service.revokeSessionBySessionId(1, "s-1")

            Then("S-4 조회 기준은 폐기 여부만이라 폐기가 된다") {
                verify(exactly = 1) { f.userSessionEntityService.revoke("s-1", "LOGOUT") }
                verify(exactly = 1) { f.sessionCacheBizService.invalidateSession("s-1") }
            }
        }
    }
})