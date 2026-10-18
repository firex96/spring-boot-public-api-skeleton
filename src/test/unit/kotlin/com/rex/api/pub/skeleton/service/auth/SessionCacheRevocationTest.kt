package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.PermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime

/**
 * 폐기한 세션이 캐시에 남지 않는다 — 폐기 쪽은 **커밋 뒤에, 표식 먼저** 캐시를 지우고, 조회 쪽은 **표식이 없을 때만** 캐시에 쓴다(한 스크립트).
 *
 * 경쟁 순서(캐시 miss → DB 에서 활성 세션 읽음 → 다른 요청의 폐기 커밋·캐시 삭제 → 앞선 요청의 캐시 쓰기)에서
 * 늦은 쓰기는 표식에 막혀 캐시에 나타나지 않는다. 스크립트 자체의 원자성·클러스터 슬롯은 `SessionCacheRevocationIntegrationTest` 가 실제 Redis 로 본다.
 */
class SessionCacheRevocationTest : BehaviorSpec({
    val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    val valueOperations = mockk<ValueOperations<String, String>>(relaxed = true)
    val userSessionEntityService = mockk<UserSessionEntityService>()
    val userGroupEntityService = mockk<UserGroupEntityService>()
    val permissionEntityService = mockk<PermissionEntityService>()
    val service =
        SessionCacheBizService(redisTemplate, JsonMapper.builder().findAndAddModules().build(), userSessionEntityService, userGroupEntityService, permissionEntityService)

    fun activeRecord(sessionId: String) =
        LocalDateTime.now().let { now ->
            UserSessionRecordDto(
                id = 1, userId = 7, sessionId = sessionId, deviceType = "WEB", deviceInfo = null, ipAddress = null, refreshTokenHash = "h",
                accessTokenIssuedAt = now, refreshTokenIssuedAt = now, accessTokenExpiresAt = now.plusMinutes(30), refreshTokenExpiresAt = now.plusDays(1),
                lastActivityAt = now, isRevoked = false, revokedAt = null, revokedReason = null, createdAt = now,
            )
        }

    beforeContainer {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.get(any()) } returns null
        every { userGroupEntityService.getActiveGroupCodesByUserId(7) } returns listOf("USER")
        every { permissionEntityService.getAllByUserId(7) } returns emptyList()
        every { userSessionEntityService.getUnrevokedBySessionId("s1") } returns activeRecord("s1")
    }

    Given("SR-1 캐시 miss 로 DB 에서 활성 세션을 읽었는데 그사이 폐기 표식이 생겼다") {
        every { redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), *anyVararg()) } returns 0L

        Then("폐기 전 값을 캐시에 쓰지도 돌려주지도 않는다 — 표식 확인과 쓰기는 한 스크립트(원자적)다") {
            service.getSession("s1").shouldBeNull()
            verify(exactly = 1) { redisTemplate.execute(any<RedisScript<Long>>(), listOf("session:{s1}", "session:revoked:{s1}"), *anyVararg()) }
            verify(exactly = 0) { valueOperations.set("session:{s1}", any(), any<Duration>()) }
        }
    }

    Given("SR-2 표식이 없으면") {
        every { redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), *anyVararg()) } returns 1L

        Then("캐시에 쓰고 세션을 돌려준다") {
            service.getSession("s1")?.sessionId shouldBe "s1"
        }
    }

    Given("SR-3 트랜잭션 안의 폐기") {
        Then("커밋 전에는 아무것도 지우지 않고, 커밋 뒤에 표식을 먼저 쓰고 캐시·권한 캐시를 지운다") {
            TransactionSynchronizationManager.initSynchronization()
            try {
                service.evictRevokedSessions(listOf("s1", "s2"), userId = 7)
                verify(exactly = 0) { redisTemplate.delete(any<Collection<String>>()) }
                verify(exactly = 0) { valueOperations.set(any(), any(), any<Duration>()) }

                TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }

            verifyOrder {
                valueOperations.set("session:revoked:{s1}", "1", any<Duration>())
                valueOperations.set("session:revoked:{s2}", "1", any<Duration>())
                redisTemplate.delete(listOf("session:{s1}", "session:{s2}", "user:permissions:7"))
            }
        }
    }

    Given("SR-4 트랜잭션 밖의 폐기") {
        Then("바로 표식을 쓰고 지운다") {
            service.invalidateSession("s9")

            verifyOrder {
                valueOperations.set("session:revoked:{s9}", "1", any<Duration>())
                redisTemplate.delete(listOf("session:{s9}"))
            }
        }
    }
})