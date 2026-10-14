package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.config.security.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * 테스트용 JWT 토큰 생성기.
 *
 * 통합 테스트에서 인증이 필요한 API를 테스트할 때 사용합니다.
 *
 * 사용 예시:
 * ```kotlin
 * val tokenGenerator = TestJwtTokenGenerator()
 * val token = tokenGenerator.generateUserToken(userId = 1L)
 *
 * mockMvc.get("/api/users/me") {
 *     header("Authorization", "Bearer $token")
 * }
 * ```
 */
class TestJwtTokenGenerator(
    private val jwtProperties: JwtProperties = JwtProperties(
        secret = "test-secret-key-for-jwt-token-signing-must-be-at-least-32-chars",
        issuer = "public-api-skeleton-test",
        audience = "skeleton-apps",
        accessTokenExpiration = 3600,
        refreshTokenExpiration = 604800,
    ),
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    /**
     * 일반 사용자 토큰 생성.
     *
     * @param userId 사용자 ID (기본값: 1)
     * @param sessionId 세션 ID (기본값: UUID 자동 생성)
     * @param groups 그룹 목록 (기본값: ["USER"])
     */
    fun generateUserToken(
        userId: Long = 1L,
        sessionId: String = UUID.randomUUID().toString(),
        groups: List<String> = listOf("USER"),
    ): String = generateAccessToken(userId, sessionId, groups)

    /**
     * 관리자 토큰 생성.
     *
     * @param userId 사용자 ID (기본값: 1)
     * @param sessionId 세션 ID (기본값: UUID 자동 생성)
     */
    fun generateAdminToken(
        userId: Long = 1L,
        sessionId: String = UUID.randomUUID().toString(),
    ): String = generateAccessToken(userId, sessionId, listOf("ADMIN"))

    /**
     * 슈퍼 관리자 토큰 생성.
     *
     * @param userId 사용자 ID (기본값: 1)
     * @param sessionId 세션 ID (기본값: UUID 자동 생성)
     */
    fun generateSuperAdminToken(
        userId: Long = 1L,
        sessionId: String = UUID.randomUUID().toString(),
    ): String = generateAccessToken(userId, sessionId, listOf("SUPER_ADMIN"))

    /**
     * 커스텀 그룹의 토큰 생성.
     *
     * @param userId 사용자 ID
     * @param sessionId 세션 ID
     * @param groups 그룹 목록
     * @param expirationSeconds 만료 시간 (초)
     */
    fun generateAccessToken(
        userId: Long,
        sessionId: String,
        groups: List<String>,
        expirationSeconds: Long = jwtProperties.accessTokenExpiration,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(expirationSeconds)

        return Jwts.builder()
            .subject(userId.toString())
            .id(sessionId)
            .issuer(jwtProperties.issuer)
            .audience().add(jwtProperties.audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .claim("groups", groups)
            .claim("client_id", "test-client")
            .signWith(secretKey)
            .compact()
    }

    /**
     * 리프레시 토큰 생성.
     *
     * @param userId 사용자 ID
     * @param sessionId 세션 ID
     */
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

    /**
     * 만료된 토큰 생성 (테스트용).
     *
     * @param userId 사용자 ID
     * @param sessionId 세션 ID
     * @param groups 그룹 목록
     */
    fun generateExpiredToken(
        userId: Long = 1L,
        sessionId: String = UUID.randomUUID().toString(),
        groups: List<String> = listOf("USER"),
    ): String {
        val now = Instant.now()
        val expiration = now.minusSeconds(3600) // 1시간 전에 만료됨

        return Jwts.builder()
            .subject(userId.toString())
            .id(sessionId)
            .issuer(jwtProperties.issuer)
            .audience().add(jwtProperties.audience).and()
            .issuedAt(Date.from(now.minusSeconds(7200)))
            .expiration(Date.from(expiration))
            .claim("groups", groups)
            .claim("client_id", "test-client")
            .signWith(secretKey)
            .compact()
    }

    companion object {
        /** 테스트에서 공용으로 사용할 수 있는 기본 인스턴스 */
        val DEFAULT = TestJwtTokenGenerator()
    }
}