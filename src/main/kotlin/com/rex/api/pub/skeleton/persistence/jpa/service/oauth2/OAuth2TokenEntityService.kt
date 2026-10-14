package com.rex.api.pub.skeleton.persistence.jpa.service.oauth2

import com.rex.api.pub.skeleton.dto.oauth2.OAuth2AccessTokenDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2AuthorizationCodeDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2RefreshTokenDto
import com.rex.api.pub.skeleton.mapper.OAuth2Mapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AccessTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AuthorizationCodeEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2RefreshTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2AccessTokenRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2AuthorizationCodeRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * OAuth2 토큰류(인가 코드·액세스·리프레시) 엔티티 서비스. 세 리포지토리가 한 흐름(발급·교환·폐기)에 함께 쓰이므로 한 서비스가 맡는다.
 * 유효성 판정(만료·사용·폐기)은 DTO 를 받은 BizService 가 한다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class OAuth2TokenEntityService(
    private val oAuth2AuthorizationCodeRepository: OAuth2AuthorizationCodeRepository,
    private val oAuth2AccessTokenRepository: OAuth2AccessTokenRepository,
    private val oAuth2RefreshTokenRepository: OAuth2RefreshTokenRepository,
    private val mapper: OAuth2Mapper,
) {
    // ===== Authorization Code =====

    @Transactional(transactionManager = "authTransactionManager")
    fun createAuthorizationCode(
        code: String,
        clientId: String,
        userId: Long,
        redirectUri: String,
        scopes: String?,
        codeChallenge: String?,
        codeChallengeMethod: String,
        state: String?,
        nonce: String?,
        expiresAt: LocalDateTime,
    ) {
        oAuth2AuthorizationCodeRepository.save(
            OAuth2AuthorizationCodeEntity(
                code = code,
                clientId = clientId,
                userId = userId,
                redirectUri = redirectUri,
                scopes = scopes,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                state = state,
                nonce = nonce,
                expiresAt = expiresAt,
            ),
        )
    }

    fun getUnusedAuthorizationCode(code: String): OAuth2AuthorizationCodeDto? =
        oAuth2AuthorizationCodeRepository.findByCodeAndIsUsedFalse(code).getOrNull()?.let { mapper.toAuthorizationCodeDto(it) }

    @Transactional(transactionManager = "authTransactionManager")
    fun markAuthorizationCodeUsed(code: String, at: LocalDateTime) {
        oAuth2AuthorizationCodeRepository.markAsUsed(code, at)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun deleteAuthorizationCodesByClientId(clientId: String) {
        oAuth2AuthorizationCodeRepository.deleteByClientId(clientId)
    }

    // ===== Access Token =====

    @Transactional(transactionManager = "authTransactionManager")
    fun createAccessToken(tokenId: String, clientId: String, userId: Long?, scopes: String?, expiresAt: LocalDateTime, refreshTokenId: String?) {
        oAuth2AccessTokenRepository.save(
            OAuth2AccessTokenEntity(
                tokenId = tokenId,
                clientId = clientId,
                userId = userId,
                scopes = scopes,
                expiresAt = expiresAt,
                refreshTokenId = refreshTokenId,
            ),
        )
    }

    fun getAccessToken(tokenId: String): OAuth2AccessTokenDto? =
        oAuth2AccessTokenRepository.findByTokenId(tokenId).getOrNull()?.let { mapper.toAccessTokenDto(it) }

    @Transactional(transactionManager = "authTransactionManager")
    fun revokeAccessToken(tokenId: String, at: LocalDateTime): Int = oAuth2AccessTokenRepository.revokeByTokenId(tokenId, at)

    @Transactional(transactionManager = "authTransactionManager")
    fun revokeAccessTokensByRefreshTokenId(refreshTokenId: String, at: LocalDateTime): Int =
        oAuth2AccessTokenRepository.revokeByRefreshTokenId(refreshTokenId, at)

    // ===== Refresh Token =====

    @Transactional(transactionManager = "authTransactionManager")
    fun createRefreshToken(tokenId: String, clientId: String, userId: Long?, scopes: String?, expiresAt: LocalDateTime) {
        oAuth2RefreshTokenRepository.save(
            OAuth2RefreshTokenEntity(tokenId = tokenId, clientId = clientId, userId = userId, scopes = scopes, expiresAt = expiresAt),
        )
    }

    fun getRefreshToken(tokenId: String): OAuth2RefreshTokenDto? =
        oAuth2RefreshTokenRepository.findByTokenId(tokenId).getOrNull()?.let { mapper.toRefreshTokenDto(it) }

    fun getUnrevokedRefreshToken(tokenId: String): OAuth2RefreshTokenDto? =
        oAuth2RefreshTokenRepository.findByTokenIdAndIsRevokedFalse(tokenId).getOrNull()?.let { mapper.toRefreshTokenDto(it) }

    @Transactional(transactionManager = "authTransactionManager")
    fun revokeRefreshToken(tokenId: String, at: LocalDateTime): Int = oAuth2RefreshTokenRepository.revokeByTokenId(tokenId, at)

    // ===== Client 단위 =====

    /** 클라이언트 삭제·비활성 시 — 액세스·리프레시 둘 다 폐기한다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun revokeAllByClientId(clientId: String, at: LocalDateTime) {
        oAuth2AccessTokenRepository.revokeByClientId(clientId, at)
        oAuth2RefreshTokenRepository.revokeByClientId(clientId, at)
    }
}