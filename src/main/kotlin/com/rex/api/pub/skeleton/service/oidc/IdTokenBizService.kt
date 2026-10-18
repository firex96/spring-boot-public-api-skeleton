package com.rex.api.pub.skeleton.service.oidc

import com.rex.api.pub.skeleton.config.security.OidcProperties
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID

@Service
class IdTokenBizService(
    private val oidcProperties: OidcProperties,
    private val jwkBizService: JwkBizService,
    private val tokenEntityService: TokenEntityService,
) {
    fun generateIdToken(
        user: UserDto,
        clientId: String,
        nonce: String? = null,
        scopes: Set<String> = emptySet(),
        accessTokenHash: String? = null,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(oidcProperties.idTokenExpiration)

        val claims = mutableMapOf<String, Any>(
            "iss" to oidcProperties.issuer,
            "sub" to user.id.toString(),
            "aud" to clientId,
            "iat" to now.epochSecond,
            "exp" to expiration.epochSecond,
            "auth_time" to now.epochSecond,
        )

        // nonce가 있으면 포함
        nonce?.let { claims["nonce"] = it }

        // at_hash (access token hash) 포함
        accessTokenHash?.let { claims["at_hash"] = it }

        // 스코프에 따른 클레임 추가
        if (scopes.contains("profile")) {
            user.name?.let { claims["name"] = it }
            user.nickname?.let { claims["nickname"] = it }
            user.profileImage?.let { claims["picture"] = it }
        }

        if (scopes.contains("email")) {
            user.email?.let {
                claims["email"] = it
                claims["email_verified"] = tokenEntityService.existsVerifiedEmail(user.id, it) // 링크 인증 기록이 정본
            }
        }

        return Jwts.builder()
            .header()
            .keyId(jwkBizService.getKeyId())
            .type("JWT")
            .and()
            .claims(claims)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(jwkBizService.getPrivateKey(), Jwts.SIG.RS256)
            .compact()
    }

    companion object {
        /** ID 토큰 서명 알고리즘 — RS256 하나(JWKS 로 검증 가능한 것만). HS256 은 access·refresh 토큰용이라 광고하지 않는다. */
        val SUPPORTED_SIGNING_ALGORITHMS: List<String> = listOf("RS256")

        /** id_token 과 userinfo(`OidcBizService`)가 실제로 내보내는 클레임의 합집합 — discovery `claims_supported`. */
        val SUPPORTED_CLAIMS: List<String> =
            listOf(
                "iss", "sub", "aud", "iat", "exp", "jti", "auth_time", "nonce", "at_hash",
                "name", "nickname", "picture", "updated_at", "email", "email_verified",
            )
    }

    fun computeAtHash(accessToken: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(accessToken.toByteArray(Charsets.US_ASCII))
        val leftHalf = hash.copyOfRange(0, hash.size / 2)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf)
    }
}