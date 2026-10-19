package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.IssuedSessionDto
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/**
 * 1st-party 세션 발급의 유일한 경로 — 인증이 **끝난** 사용자에게 세션 행·토큰·세션 캐시를 만든다.
 *
 * 비밀번호·소셜 로그인 모두 여기로 온다. 인증 판단(비밀번호 비교·소셜 검증·로그인 보호)은 호출자가 끝낸 뒤에 부른다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class SessionIssueBizService(
    private val userEntityService: UserEntityService,
    private val userSessionEntityService: UserSessionEntityService,
    private val userGroupEntityService: UserGroupEntityService,
    private val jwtTokenBizService: JwtTokenBizService,
    private val sessionCacheBizService: SessionCacheBizService,
) {
    @Transactional(transactionManager = "authTransactionManager")
    fun issue(
        user: UserDto,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): IssuedSessionDto {
        val sessionId = UUID.randomUUID().toString()
        val groups = userGroupEntityService.getActiveGroupCodesByUserId(user.id)
        val now = LocalDateTime.now()
        val accessTokenExpiration = now.plusSeconds(jwtTokenBizService.getAccessTokenExpirationSeconds())
        val refreshTokenExpiration = now.plusSeconds(jwtTokenBizService.getRefreshTokenExpirationSeconds())

        val accessToken = jwtTokenBizService.generateAccessToken(userId = user.id, sessionId = sessionId, groups = groups)
        val refreshToken = jwtTokenBizService.generateRefreshToken(userId = user.id, sessionId = sessionId)

        userSessionEntityService.createSession(
            UserSessionRecordDto(
                id = 0,
                userId = user.id,
                sessionId = sessionId,
                deviceType = deviceType,
                deviceInfo = deviceInfo,
                ipAddress = ipAddress,
                refreshTokenHash = hashToken(refreshToken),
                accessTokenIssuedAt = now,
                refreshTokenIssuedAt = now,
                accessTokenExpiresAt = accessTokenExpiration,
                refreshTokenExpiresAt = refreshTokenExpiration,
                lastActivityAt = now,
                isRevoked = false,
                revokedAt = null,
                revokedReason = null,
                createdAt = now,
            ),
        )
        sessionCacheBizService.cacheSession(
            SessionInfo(
                userId = user.id,
                sessionId = sessionId,
                groups = groups,
                permissions = emptyList(), // 초기에는 빈 리스트, getSession 호출 시 로드됨
                deviceType = deviceType,
                ipAddress = ipAddress,
                issuedAt = now.atZone(ZoneId.systemDefault()).toInstant(),
                expiresAt = accessTokenExpiration.atZone(ZoneId.systemDefault()).toInstant(),
            ),
        )
        userEntityService.updateLastLoginAt(user.id, now)

        return IssuedSessionDto(
            sessionId = sessionId,
            tokens =
            TokenResponseDto(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = jwtTokenBizService.getAccessTokenExpirationSeconds(),
            ),
        )
    }

    /**
     * SHA-256으로 토큰을 해싱합니다.
     *
     * BCrypt는 72바이트 제한이 있어 JWT 토큰에 적합하지 않습니다.
     * Refresh token 저장에는 SHA-256을 사용합니다.
     */
    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}