package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64

/**
 * 세션 발급의 유일한 경로 — 세션 행·세션 캐시·마지막 로그인 시각이 같은 세션 ID 로 만들어지고, 행에는 refresh 토큰의 해시만 남는다.
 */
class SessionIssueBizServiceTest : StringSpec({
    val userEntityService = mockk<UserEntityService>()
    val userSessionEntityService = mockk<UserSessionEntityService>()
    val userGroupEntityService = mockk<UserGroupEntityService>()
    val jwtTokenBizService = mockk<JwtTokenBizService>()
    val sessionCacheBizService = mockk<SessionCacheBizService>()
    val service = SessionIssueBizService(userEntityService, userSessionEntityService, userGroupEntityService, jwtTokenBizService, sessionCacheBizService)

    val user =
        UserDto(
            id = 7L, loginId = "user1", passwordHash = "hash", email = null, phone = null, name = null, nickname = null,
            profileImage = null, status = UserStatus.ACTIVE, lastLoginAt = null, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
        )

    "세션 행·캐시·마지막 로그인을 같은 세션 ID 로 만들고 토큰을 돌려준다" {
        val record = slot<UserSessionRecordDto>()
        val cached = slot<SessionInfo>()
        every { userGroupEntityService.getActiveGroupCodesByUserId(7L) } returns listOf("USER")
        every { jwtTokenBizService.getAccessTokenExpirationSeconds() } returns 3600L
        every { jwtTokenBizService.getRefreshTokenExpirationSeconds() } returns 604800L
        every { jwtTokenBizService.generateAccessToken(7L, any(), listOf("USER")) } returns "access-token"
        every { jwtTokenBizService.generateRefreshToken(7L, any()) } returns "refresh-token"
        every { userSessionEntityService.createSession(capture(record)) } answers { firstArg() }
        justRun { sessionCacheBizService.cacheSession(capture(cached)) }
        justRun { userEntityService.updateLastLoginAt(7L, any()) }

        val issued = service.issue(user, "WEB", "agent", "203.0.113.10")

        issued.tokens.accessToken shouldBe "access-token"
        issued.tokens.refreshToken shouldBe "refresh-token"
        issued.tokens.expiresIn shouldBe 3600L
        record.captured.sessionId shouldBe issued.sessionId
        cached.captured.sessionId shouldBe issued.sessionId
        cached.captured.groups shouldBe listOf("USER")
        record.captured.deviceType shouldBe "WEB"
        record.captured.ipAddress shouldBe "203.0.113.10"
        record.captured.refreshTokenHash shouldNotBe "refresh-token"
        record.captured.refreshTokenHash shouldBe
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest("refresh-token".toByteArray()))
        verify(exactly = 1) { jwtTokenBizService.generateAccessToken(7L, issued.sessionId, listOf("USER")) }
        verify(exactly = 1) { userEntityService.updateLastLoginAt(7L, any()) }
    }
})