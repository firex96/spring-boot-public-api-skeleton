package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** 콜백 토큰 교환 대기 — 사용자 세션 키에 60초, 꺼낼 때는 `GETDEL` 로 한 번만. */
class TokenHandoffBizServiceTest : BehaviorSpec({
    val redisTemplate = mockk<StringRedisTemplate>()
    val valueOperations = mockk<ValueOperations<String, String>>()
    val service = TokenHandoffBizService(redisTemplate, JsonMapper.builder().findAndAddModules().build())
    val stored = mutableMapOf<String, String>()
    val tokens = TokenResponseDto(accessToken = "access", refreshToken = "refresh", expiresIn = 3600)

    beforeContainer {
        clearAllMocks()
        stored.clear()
        every { redisTemplate.opsForValue() } returns valueOperations
        val key = slot<String>()
        val value = slot<String>()
        every { valueOperations.set(capture(key), capture(value), any<Duration>()) } answers { stored[key.captured] = value.captured }
        every { valueOperations.getAndDelete(any()) } answers { stored.remove(firstArg()) }
    }

    Given("TH-1 맡기기") {
        Then("browser:token-handoff:{userSessionId} 에 TTL 60초로 저장한다") {
            service.put("user-session-1", tokens)

            verify(exactly = 1) { valueOperations.set("browser:token-handoff:user-session-1", any(), Duration.ofSeconds(60)) }
        }
    }

    Given("TH-2 꺼내기") {
        Then("첫 번째는 토큰, 두 번째는 null — getAndDelete 로 꺼낸다") {
            service.put("user-session-1", tokens)

            service.take("user-session-1") shouldBe tokens
            service.take("user-session-1").shouldBeNull()
            verify(exactly = 2) { valueOperations.getAndDelete("browser:token-handoff:user-session-1") }
        }
    }

    Given("TH-3 다른 사용자 세션") {
        Then("null 이다") {
            service.put("user-session-1", tokens)

            service.take("user-session-2").shouldBeNull()
        }
    }
})