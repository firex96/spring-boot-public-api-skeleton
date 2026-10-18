package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * 소셜 state 의 발급·소비 계약 — 발급한 브라우저 세션·provider 에서만, 한 번만 통과한다.
 * 동시 요청에서 정확히 하나만 통과하는 성질은 `GETDEL`(원자적)에 기대므로 소비는 반드시 `getAndDelete` 여야 한다.
 */
class SocialAuthorizationStateBizServiceTest : BehaviorSpec({
    val redisTemplate = mockk<StringRedisTemplate>()
    val valueOperations = mockk<ValueOperations<String, String>>()
    val jsonMapper = JsonMapper.builder().findAndAddModules().build()
    val service = SocialAuthorizationStateBizService(redisTemplate, jsonMapper)

    val stored = mutableMapOf<String, String>()

    beforeContainer {
        clearAllMocks()
        stored.clear()
        every { redisTemplate.opsForValue() } returns valueOperations
        val key = slot<String>()
        val value = slot<String>()
        every { valueOperations.set(capture(key), capture(value), any<Duration>()) } answers { stored[key.captured] = value.captured }
        every { valueOperations.getAndDelete(any()) } answers { stored.remove(firstArg()) }
    }

    Given("ST-1 발급") {
        Then("32바이트 base64url state 를 browser:social-state:{state} 에 TTL 300초로 저장한다") {
            val state = service.issue("browser-session-a", SocialProvider.GOOGLE)

            state shouldMatch Regex("^[A-Za-z0-9_-]{43}$")
            verify(exactly = 1) { valueOperations.set("browser:social-state:$state", any(), Duration.ofSeconds(300)) }
        }
    }

    Given("ST-2 같은 세션·같은 provider 의 소비") {
        Then("true 이고, 두 번째 소비는 false 다(재사용 불가)") {
            val state = service.issue("browser-session-a", SocialProvider.GOOGLE)

            service.consume("browser-session-a", SocialProvider.GOOGLE, state) shouldBe true
            service.consume("browser-session-a", SocialProvider.GOOGLE, state) shouldBe false
            verify(exactly = 2) { valueOperations.getAndDelete("browser:social-state:$state") }
        }
    }

    Given("ST-3 다른 브라우저 세션의 소비") {
        Then("false 이고 키는 이미 지워져 원래 세션도 다시 쓸 수 없다") {
            val state = service.issue("browser-session-a", SocialProvider.GOOGLE)

            service.consume("browser-session-b", SocialProvider.GOOGLE, state) shouldBe false
            service.consume("browser-session-a", SocialProvider.GOOGLE, state) shouldBe false
        }
    }

    Given("ST-4 다른 provider 의 소비") {
        Then("false 다") {
            val state = service.issue("browser-session-a", SocialProvider.GOOGLE)

            service.consume("browser-session-a", SocialProvider.NAVER, state) shouldBe false
        }
    }

    Given("ST-5 state 없음·만료(키 없음)") {
        Then("false 다") {
            service.consume("browser-session-a", SocialProvider.GOOGLE, null) shouldBe false
            service.consume("browser-session-a", SocialProvider.GOOGLE, "") shouldBe false
            service.consume("browser-session-a", SocialProvider.GOOGLE, "never-issued") shouldBe false
        }
    }
})