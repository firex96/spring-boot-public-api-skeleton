package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.time.Instant
import java.util.UUID

/**
 * 폐기 표식과 조건부 캐시 쓰기 — 실제 Redis(단일 노드 클러스터)에서 스크립트가 돌고, 두 키가 해시 태그로 같은 슬롯에 있는지.
 *
 * 폐기가 끝난 뒤에는 폐기 전에 읽어 둔 값이 캐시에 다시 쓰이지 않아야 한다(`cacheSessionUnlessRevoked` 가 false, 키 없음).
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class SessionCacheRevocationIntegrationTest(
    private val sessionCacheBizService: SessionCacheBizService,
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({
    fun sessionInfo(sessionId: String) =
        SessionInfo(
            userId = 7,
            sessionId = sessionId,
            groups = listOf("USER"),
            permissions = emptyList(),
            deviceType = "WEB",
            ipAddress = null,
            issuedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(600),
            authenticatedAt = Instant.now(),
        )

    Given("SRI-1 폐기되지 않은 세션") {
        Then("캐시에 쓴다") {
            val id = UUID.randomUUID().toString()

            sessionCacheBizService.cacheSessionUnlessRevoked(sessionInfo(id)) shouldBe true
            redisTemplate.hasKey(SessionCacheBizService.sessionKey(id)) shouldBe true
        }
    }

    Given("SRI-2 폐기를 마친 세션의 늦은 캐시 쓰기") {
        Then("쓰지 않는다 — 표식이 막는다") {
            val id = UUID.randomUUID().toString()
            val staleReadBeforeRevocation = sessionInfo(id)
            sessionCacheBizService.cacheSessionUnlessRevoked(sessionInfo(id))

            sessionCacheBizService.evictRevokedSessions(listOf(id), userId = null)

            redisTemplate.hasKey(SessionCacheBizService.sessionKey(id)) shouldBe false
            sessionCacheBizService.cacheSessionUnlessRevoked(staleReadBeforeRevocation) shouldBe false
            redisTemplate.hasKey(SessionCacheBizService.sessionKey(id)) shouldBe false
        }
    }
})