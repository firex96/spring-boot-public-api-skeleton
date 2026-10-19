package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime

/**
 * 일괄 폐기 — 폐기 **전에** 모은 세션 ID **만** 폐기하고, 같은 ID 로 캐시를 지운다.
 *
 * 사용자 단위 bulk UPDATE 를 쓰면 수집과 UPDATE 사이에 로그인한 세션이 DB 에서는 폐기되고 캐시 삭제 목록에서는 빠져
 * 폐기된 세션이 캐시로 계속 통과한다. 폐기 대상과 캐시 삭제 대상이 같은 목록이어야 한다.
 */
class UserSessionRevocationBizServiceTest : BehaviorSpec({
    val userSessionEntityService = mockk<UserSessionEntityService>()
    val sessionCacheBizService = mockk<SessionCacheBizService>()
    val service = UserSessionRevocationBizService(userSessionEntityService, sessionCacheBizService)

    fun record(sessionId: String) =
        LocalDateTime.now().let { now ->
            UserSessionRecordDto(
                id = 1, userId = 7, sessionId = sessionId, deviceType = null, deviceInfo = null, ipAddress = null, refreshTokenHash = "h",
                accessTokenIssuedAt = now, refreshTokenIssuedAt = now, accessTokenExpiresAt = now, refreshTokenExpiresAt = now,
                lastActivityAt = now, isRevoked = false, revokedAt = null, revokedReason = null, createdAt = now,
            )
        }

    beforeContainer {
        clearAllMocks()
        every { userSessionEntityService.getUnrevokedByUserId(7) } returns listOf(record("current"), record("phone"), record("laptop"))
        justRun { userSessionEntityService.revokeAll(any(), any()) }
        justRun { sessionCacheBizService.evictRevokedSessions(any(), any()) }
    }

    Given("RA-1 현재 세션을 빼고 폐기") {
        Then("모은 ID 에서 현재 세션만 빼고, 그 목록으로 폐기·캐시 제거를 한다") {
            service.revokeAll(7, "LOGOUT_ALL", exceptSessionId = "current")

            verify(exactly = 1) { userSessionEntityService.revokeAll(listOf("phone", "laptop"), "LOGOUT_ALL") }
            verify(exactly = 1) { sessionCacheBizService.evictRevokedSessions(listOf("phone", "laptop"), 7) }
        }
    }

    Given("RA-2 남길 세션 없이 모두 폐기") {
        Then("모은 ID 전부를 같은 목록으로 폐기·캐시 제거한다") {
            service.revokeAll(7, "PASSWORD_CHANGED", exceptSessionId = null)

            verify(exactly = 1) { userSessionEntityService.revokeAll(listOf("current", "phone", "laptop"), "PASSWORD_CHANGED") }
            verify(exactly = 1) { sessionCacheBizService.evictRevokedSessions(listOf("current", "phone", "laptop"), 7) }
        }
    }
})