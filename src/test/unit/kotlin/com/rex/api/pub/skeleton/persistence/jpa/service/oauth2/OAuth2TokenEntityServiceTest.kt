package com.rex.api.pub.skeleton.persistence.jpa.service.oauth2

import com.rex.api.pub.skeleton.mapper.OAuth2Mapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AccessTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AuthorizationCodeEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2AccessTokenRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2AuthorizationCodeRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2RefreshTokenRepository
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

/** OAuth2 토큰류 EntityService — 클라이언트 단위 일괄 폐기가 액세스·리프레시 두 리포지토리를 같은 시각으로 부르는지, 생성·조회가 위임되는지. */
class OAuth2TokenEntityServiceTest : DescribeSpec({

    val codeRepository = mockk<OAuth2AuthorizationCodeRepository>()
    val accessTokenRepository = mockk<OAuth2AccessTokenRepository>()
    val refreshTokenRepository = mockk<OAuth2RefreshTokenRepository>()
    val mapper = Mappers.getMapper(OAuth2Mapper::class.java)
    val service = OAuth2TokenEntityService(codeRepository, accessTokenRepository, refreshTokenRepository, mapper)

    val now = LocalDateTime.of(2026, 9, 17, 12, 0)

    beforeTest { clearMocks(codeRepository, accessTokenRepository, refreshTokenRepository) }

    describe("revokeAllByClientId") {
        it("액세스·리프레시 토큰 리포지토리를 같은 clientId·시각으로 각각 한 번 부른다") {
            every { accessTokenRepository.revokeByClientId("client-a", now) } returns 4
            every { refreshTokenRepository.revokeByClientId("client-a", now) } returns 2

            service.revokeAllByClientId("client-a", now)

            verify(exactly = 1) { accessTokenRepository.revokeByClientId("client-a", now) }
            verify(exactly = 1) { refreshTokenRepository.revokeByClientId("client-a", now) }
        }
    }

    describe("revokeAccessTokensByRefreshTokenId / revokeRefreshToken") {
        it("갱신 건수를 그대로 돌려준다") {
            every { accessTokenRepository.revokeByRefreshTokenId("rt-1", now) } returns 3
            every { refreshTokenRepository.revokeByTokenId("rt-1", now) } returns 1

            service.revokeAccessTokensByRefreshTokenId("rt-1", now) shouldBe 3
            service.revokeRefreshToken("rt-1", now) shouldBe 1
        }
    }

    describe("createAccessToken") {
        it("입력 필드를 그대로 담은 엔티티를 저장한다 — tokenType 기본값 Bearer, 미폐기") {
            val saved = slot<OAuth2AccessTokenEntity>()
            every { accessTokenRepository.save(capture(saved)) } answers { saved.captured }

            service.createAccessToken("at-1", "client-a", 7L, "openid profile", now.plusHours(1), "rt-1")

            saved.captured.tokenId shouldBe "at-1"
            saved.captured.clientId shouldBe "client-a"
            saved.captured.userId shouldBe 7L
            saved.captured.scopes shouldBe "openid profile"
            saved.captured.refreshTokenId shouldBe "rt-1"
            saved.captured.tokenType shouldBe "Bearer"
            saved.captured.isRevoked shouldBe false
        }
    }

    describe("getUnusedAuthorizationCode / markAuthorizationCodeUsed") {
        it("미사용 코드는 DTO 로 돌려주고(실제 매퍼), 없으면 null, 사용 처리는 리포지토리에 위임한다") {
            val entity =
                OAuth2AuthorizationCodeEntity(
                    id = 1L,
                    code = "code-1",
                    clientId = "client-a",
                    userId = 7L,
                    redirectUri = "https://app.example.com/cb",
                    codeChallengeMethod = "S256",
                    expiresAt = now.plusMinutes(5),
                )
            every { codeRepository.findByCodeAndIsUsedFalse("code-1") } returns Optional.of(entity)
            every { codeRepository.findByCodeAndIsUsedFalse("used") } returns Optional.empty()
            every { codeRepository.markAsUsed("code-1", now) } returns 1

            val dto = service.getUnusedAuthorizationCode("code-1")
            dto?.clientId shouldBe "client-a"
            dto?.isUsed shouldBe false
            service.getUnusedAuthorizationCode("used").shouldBeNull()

            service.markAuthorizationCodeUsed("code-1", now)
            verify(exactly = 1) { codeRepository.markAsUsed("code-1", now) }
        }
    }
})