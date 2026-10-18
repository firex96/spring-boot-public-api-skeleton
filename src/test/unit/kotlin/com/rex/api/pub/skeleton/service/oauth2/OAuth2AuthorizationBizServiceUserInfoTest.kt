package com.rex.api.pub.skeleton.service.oauth2

import com.rex.api.pub.skeleton.config.security.JwtProperties
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2AccessTokenDto
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.oauth2.OAuth2TokenEntityService
import com.rex.api.pub.skeleton.service.oidc.IdTokenBizService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date

/**
 * `resolveUserInfoGrant` — userinfo 는 이 인가 서버가 발급한 활성 access 토큰(사용자 주체 + openid scope)만 받는다(OIDC Core §5.3).
 */
class OAuth2AuthorizationBizServiceUserInfoTest : BehaviorSpec({
    val secret = "unit-test-secret-key-for-oauth2-tokens-0123456789"
    val tokenEntityService = mockk<OAuth2TokenEntityService>()
    val service =
        OAuth2AuthorizationBizService(
            oAuth2ClientBizService = mockk(),
            oAuth2TokenEntityService = tokenEntityService,
            userEntityService = mockk<UserEntityService>(),
            jwtProperties = JwtProperties(secret = secret),
            idTokenBizService = mockk<IdTokenBizService>(),
        )

    fun jwt(jti: String, key: String = secret, expiresAt: Instant = Instant.now().plusSeconds(600)): String =
        Jwts.builder().id(jti).issuedAt(Date()).expiration(Date.from(expiresAt))
            .signWith(Keys.hmacShaKeyFor(key.toByteArray(StandardCharsets.UTF_8))).compact()

    fun record(jti: String, userId: Long?, scopes: String?, revoked: Boolean = false, expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10)) =
        OAuth2AccessTokenDto(
            id = 1, tokenId = jti, clientId = "client", userId = userId, scopes = scopes, tokenType = "Bearer",
            issuedAt = LocalDateTime.now(), expiresAt = expiresAt, isRevoked = revoked, revokedAt = null, refreshTokenId = null,
        )

    fun errorOf(token: String) = shouldThrow<OAuth2Exception> { service.resolveUserInfoGrant(token) }.error

    Given("UI-1 활성 토큰 · 사용자 주체 · openid") {
        every { tokenEntityService.getAccessToken("t1") } returns record("t1", 7L, "openid,profile")
        Then("userId 와 scope 집합") {
            val grant = service.resolveUserInfoGrant(jwt("t1"))
            grant.userId shouldBe 7L
            grant.scopes shouldBe setOf("openid", "profile")
        }
    }

    Given("UI-2 서명 불일치·만료") {
        Then("invalid_token") {
            errorOf(jwt("t1", key = "another-secret-key-for-signing-0123456789-abcdef")) shouldBe "invalid_token"
            errorOf(jwt("t1", expiresAt = Instant.now().minusSeconds(60))) shouldBe "invalid_token"
        }
    }

    Given("UI-3 발급 기록 없음(refresh·1st-party 토큰) · 폐기된 토큰") {
        every { tokenEntityService.getAccessToken("unknown") } returns null
        every { tokenEntityService.getAccessToken("revoked") } returns record("revoked", 7L, "openid", revoked = true)
        Then("invalid_token") {
            errorOf(jwt("unknown")) shouldBe "invalid_token"
            errorOf(jwt("revoked")) shouldBe "invalid_token"
        }
    }

    Given("UI-4 사용자 주체 없음(client_credentials)") {
        every { tokenEntityService.getAccessToken("cc") } returns record("cc", null, "openid")
        Then("invalid_token") { errorOf(jwt("cc")) shouldBe "invalid_token" }
    }

    Given("UI-5 openid scope 없음") {
        every { tokenEntityService.getAccessToken("no-openid") } returns record("no-openid", 7L, "profile email")
        Then("insufficient_scope") { errorOf(jwt("no-openid")) shouldBe "insufficient_scope" }
    }
})