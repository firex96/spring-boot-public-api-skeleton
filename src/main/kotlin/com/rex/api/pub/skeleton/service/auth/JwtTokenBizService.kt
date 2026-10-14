package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.config.security.JwtProperties
import com.rex.api.pub.skeleton.dto.auth.TokenPayload
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtTokenBizService(
    private val jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun generateAccessToken(
        userId: Long,
        sessionId: String,
        groups: List<String>,
        clientId: String = "default",
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(jwtProperties.accessTokenExpiration)

        return Jwts.builder()
            .subject(userId.toString())
            .id(sessionId)
            .issuer(jwtProperties.issuer)
            .audience().add(jwtProperties.audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .claim("groups", groups)
            .claim("client_id", clientId)
            .signWith(secretKey)
            .compact()
    }

    fun generateRefreshToken(
        userId: Long,
        sessionId: String,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(jwtProperties.refreshTokenExpiration)

        return Jwts.builder()
            .subject(userId.toString())
            .id(sessionId)
            .issuer(jwtProperties.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .claim("type", "refresh")
            .signWith(secretKey)
            .compact()
    }

    fun validateToken(token: String): TokenPayload? =
        try {
            val claims = parseToken(token)
            TokenPayload(
                userId = claims.subject.toLong(),
                sessionId = claims.id,
                groups = extractGroups(claims),
                issuedAt = claims.issuedAt.toInstant(),
                expiresAt = claims.expiration.toInstant(),
                isRefreshToken = claims["type"] == "refresh",
            )
        } catch (e: ExpiredJwtException) {
            null
        } catch (e: JwtException) {
            null
        } catch (e: Exception) {
            null
        }

    fun isTokenExpired(token: String): Boolean =
        try {
            val claims = parseToken(token)
            claims.expiration.before(Date())
        } catch (e: ExpiredJwtException) {
            true
        } catch (e: Exception) {
            true
        }

    fun extractSessionId(token: String): String? =
        try {
            parseToken(token).id
        } catch (e: Exception) {
            null
        }

    fun extractUserId(token: String): Long? =
        try {
            parseToken(token).subject.toLong()
        } catch (e: Exception) {
            null
        }

    fun getAccessTokenExpirationSeconds(): Long = jwtProperties.accessTokenExpiration

    fun getRefreshTokenExpirationSeconds(): Long = jwtProperties.refreshTokenExpiration

    private fun parseToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    @Suppress("UNCHECKED_CAST")
    private fun extractGroups(claims: Claims): List<String> = claims["groups"] as? List<String> ?: emptyList()
}