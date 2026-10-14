package com.rex.api.pub.skeleton.service.security

import com.rex.api.pub.skeleton.config.security.SecurityProperties
import com.rex.api.pub.skeleton.dto.security.BlockedIpDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.security.BlockedIpEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * `LoginAttemptBizService.recordFailedAttempt` 의 IP 계약 — IP 를 모르면(null) IP 카운터·IP 차단을 건너뛰고 사용자 카운터·잠금만 기록한다.
 * 공유 버킷(`"unknown"` 류)으로 세면 임계에서 그 값이 `blocked_ips` 에 저장돼 IP 없는 로그인이 전부 403 이 된다.
 */
class LoginAttemptBizServiceTest : BehaviorSpec({
    val redisTemplate = mockk<StringRedisTemplate>()
    val valueOperations = mockk<ValueOperations<String, String>>()
    val blockedIpEntityService = mockk<BlockedIpEntityService>(relaxed = true)
    val auditBizService = mockk<AuditBizService>(relaxUnitFun = true)

    fun service(properties: SecurityProperties = SecurityProperties()) = LoginAttemptBizService(redisTemplate, blockedIpEntityService, auditBizService, properties)

    beforeContainer {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns valueOperations
        every { redisTemplate.expire(any(), any<java.time.Duration>()) } returns true
        every { valueOperations.increment(any()) } returns 1L
        every { valueOperations.set(any(), any(), any<java.time.Duration>()) } returns Unit
    }

    Given("LA-1 IP 를 모르는 실패") {
        Then("사용자 키만 증가하고 IP 키·IP 차단은 건드리지 않는다") {
            service().recordFailedAttempt("u", null)
            verify(exactly = 1) { valueOperations.increment("login:attempt:user:u") }
            verify(exactly = 0) { valueOperations.increment(match { it.startsWith("login:attempt:ip:") }) }
            verify(exactly = 0) { blockedIpEntityService.create(any()) }
        }
    }

    Given("LA-2 IP 를 아는 실패") {
        Then("사용자 키·IP 키가 각 1회 증가한다") {
            service().recordFailedAttempt("u", "203.0.113.9")
            verify(exactly = 1) { valueOperations.increment("login:attempt:user:u") }
            verify(exactly = 1) { valueOperations.increment("login:attempt:ip:203.0.113.9") }
            verify(exactly = 0) { blockedIpEntityService.create(any()) }
        }
    }

    Given("LA-3 IP 카운터가 임계(maxIpLoginAttempts)에 닿는 실패") {
        every { valueOperations.increment("login:attempt:user:u") } returns 1L
        every { valueOperations.increment("login:attempt:ip:203.0.113.9") } returns 10L
        every { blockedIpEntityService.isBlocked("203.0.113.9") } returns false

        Then("BRUTE_FORCE 사유로 IP 차단 행을 저장한다") {
            service().recordFailedAttempt("u", "203.0.113.9")
            val saved = slot<BlockedIpDto>()
            verify(exactly = 1) { blockedIpEntityService.create(capture(saved)) }
            saved.captured.ipAddress shouldBe "203.0.113.9"
            saved.captured.blockReason shouldBe BlockReason.BRUTE_FORCE
        }
    }

    Given("LA-4 사용자 카운터가 임계(maxLoginAttempts)에 닿는 실패 — IP 없음") {
        every { valueOperations.increment("login:attempt:user:u") } returns 5L

        Then("계정 잠금 키를 쓰고 감사 로그를 남긴다 — IP 차단은 없다") {
            service().recordFailedAttempt("u", null)
            verify(exactly = 1) { valueOperations.set("login:locked:user:u", "locked", any<java.time.Duration>()) }
            verify(exactly = 1) { auditBizService.logAccountLocked(null, "u", null, any()) }
            verify(exactly = 0) { blockedIpEntityService.create(any()) }
        }
    }

    Given("LA-5 보호 비활성(enableBruteForceProtection = false)") {
        Then("아무것도 기록하지 않는다") {
            service(SecurityProperties(enableBruteForceProtection = false)).recordFailedAttempt("u", "203.0.113.9")
            verify(exactly = 0) { valueOperations.increment(any()) }
        }
    }

    Given("LA-6 보호 비활성인데 Redis 에 잠금 키가 남아 있다") {
        every { redisTemplate.hasKey("login:locked:user:u") } returns true

        Then("isUserLocked 는 false — 남은 잠금 키가 보호를 끈 프로파일의 로그인을 막지 않는다(isIpBlocked 와 같은 계약)") {
            service(SecurityProperties(enableBruteForceProtection = false)).isUserLocked("u") shouldBe false
            service().isUserLocked("u") shouldBe true
        }
    }
})