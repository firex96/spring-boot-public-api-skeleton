package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSessionEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserSessionRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.Optional

/**
 * 세션 EntityService — 폐기·갱신이 리포지토리에 그대로 위임되는지, 생성 시 입력의 폐기 정보가 무시되는지.
 * 리포지토리는 mockk, 매퍼는 실제 MapStruct 구현체(Entity→DTO 변환까지 함께 검증).
 */
class UserSessionEntityServiceTest : DescribeSpec({

    val userSessionRepository = mockk<UserSessionRepository>()
    val mapper = Mappers.getMapper(AuthMapper::class.java)
    val service = UserSessionEntityService(userSessionRepository, mapper)

    val issuedAt = LocalDateTime.of(2026, 9, 17, 10, 0)

    fun record(isRevoked: Boolean = false) =
        UserSessionRecordDto(
            id = 99L,
            userId = 7L,
            sessionId = "sess-1",
            deviceType = "WEB",
            deviceInfo = "Chrome",
            ipAddress = "10.0.0.1",
            refreshTokenHash = "hash",
            accessTokenIssuedAt = issuedAt,
            refreshTokenIssuedAt = issuedAt,
            accessTokenExpiresAt = issuedAt.plusMinutes(30),
            refreshTokenExpiresAt = issuedAt.plusDays(14),
            lastActivityAt = issuedAt,
            isRevoked = isRevoked,
            revokedAt = if (isRevoked) issuedAt else null,
            revokedReason = if (isRevoked) "LOGOUT" else null,
            createdAt = issuedAt,
        )

    fun entity(sessionId: String = "sess-1", isRevoked: Boolean = false) =
        UserSessionEntity(
            id = 1L,
            userId = 7L,
            sessionId = sessionId,
            refreshTokenHash = "hash",
            accessTokenIssuedAt = issuedAt,
            refreshTokenIssuedAt = issuedAt,
            accessTokenExpiresAt = issuedAt.plusMinutes(30),
            refreshTokenExpiresAt = issuedAt.plusDays(14),
            lastActivityAt = issuedAt,
            isRevoked = isRevoked,
        )

    beforeTest { clearMocks(userSessionRepository) }

    describe("createSession") {
        it("입력의 id·폐기 정보는 무시하고 살아 있는 세션으로 저장한 뒤 DTO 로 돌려준다") {
            val saved = slot<UserSessionEntity>()
            every { userSessionRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.createSession(record(isRevoked = true))

            saved.captured.id shouldBe 0L
            saved.captured.isRevoked shouldBe false
            saved.captured.revokedAt.shouldBeNull()
            saved.captured.revokedReason.shouldBeNull()
            saved.captured.sessionId shouldBe "sess-1"
            result.sessionId shouldBe "sess-1"
            result.isRevoked shouldBe false
            result.refreshTokenHash shouldBe "hash"
        }
    }

    describe("revoke / revokeAllByUserId / revokeAllByUserIdExcept") {
        it("revoke 는 sessionId·사유를 그대로 리포지토리 벌크 갱신에 넘긴다") {
            every { userSessionRepository.revokeSession("sess-1", any(), "LOGOUT") } returns 1

            service.revoke("sess-1", "LOGOUT")

            verify(exactly = 1) { userSessionRepository.revokeSession("sess-1", any(), "LOGOUT") }
        }

        it("revokeAllByUserId 는 userId·사유로 위임한다") {
            every { userSessionRepository.revokeAllUserSessions(7L, any(), "PASSWORD_CHANGED") } returns 3

            service.revokeAllByUserId(7L, "PASSWORD_CHANGED")

            verify(exactly = 1) { userSessionRepository.revokeAllUserSessions(7L, any(), "PASSWORD_CHANGED") }
        }

        it("revokeAllByUserIdExcept 는 제외 세션까지 그대로 넘긴다") {
            every { userSessionRepository.revokeAllUserSessionsExcept(7L, "keep-me", any(), "LOGOUT_ALL") } returns 2

            service.revokeAllByUserIdExcept(7L, "keep-me", "LOGOUT_ALL")

            verify(exactly = 1) { userSessionRepository.revokeAllUserSessionsExcept(7L, "keep-me", any(), "LOGOUT_ALL") }
        }
    }

    describe("refreshAccessToken") {
        it("살아 있는 세션이면 발급·만료·마지막 활동 시각을 대입하고 저장한다") {
            val session = entity()
            val newIssuedAt = issuedAt.plusHours(1)
            val newExpiresAt = newIssuedAt.plusMinutes(30)
            every { userSessionRepository.findBySessionIdAndIsRevokedFalse("sess-1") } returns Optional.of(session)
            every { userSessionRepository.save(session) } returns session

            service.refreshAccessToken("sess-1", newIssuedAt, newExpiresAt)

            session.accessTokenIssuedAt shouldBe newIssuedAt
            session.accessTokenExpiresAt shouldBe newExpiresAt
            session.lastActivityAt shouldBe newIssuedAt
            verify(exactly = 1) { userSessionRepository.save(session) }
        }

        it("폐기된(조회되지 않는) 세션이면 저장하지 않는다 — 멱등") {
            every { userSessionRepository.findBySessionIdAndIsRevokedFalse("gone") } returns Optional.empty()

            service.refreshAccessToken("gone", issuedAt, issuedAt.plusMinutes(30))

            verify(exactly = 0) { userSessionRepository.save(any()) }
        }
    }

    describe("getUnrevokedBySessionId") {
        it("있으면 DTO 로, 없으면 null") {
            every { userSessionRepository.findBySessionIdAndIsRevokedFalse("sess-1") } returns Optional.of(entity())
            every { userSessionRepository.findBySessionIdAndIsRevokedFalse("none") } returns Optional.empty()

            service.getUnrevokedBySessionId("sess-1")?.userId shouldBe 7L
            service.getUnrevokedBySessionId("none").shouldBeNull()
        }
    }
})