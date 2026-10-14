package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AccessTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface OAuth2AccessTokenRepository : JpaRepository<OAuth2AccessTokenEntity, Long> {
    fun findByTokenId(tokenId: String): Optional<OAuth2AccessTokenEntity>

    fun findByTokenIdAndIsRevokedFalse(tokenId: String): Optional<OAuth2AccessTokenEntity>

    fun findByClientIdAndIsRevokedFalse(clientId: String): List<OAuth2AccessTokenEntity>

    fun findByUserIdAndIsRevokedFalse(userId: Long): List<OAuth2AccessTokenEntity>

    fun findByRefreshTokenId(refreshTokenId: String): List<OAuth2AccessTokenEntity>

    @Modifying
    @Query(
        "UPDATE OAuth2AccessTokenEntity t SET t.isRevoked = true, t.revokedAt = :revokedAt WHERE t.tokenId = :tokenId",
    )
    fun revokeByTokenId(
        @Param("tokenId") tokenId: String,
        @Param("revokedAt") revokedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "UPDATE OAuth2AccessTokenEntity t SET t.isRevoked = true, t.revokedAt = :revokedAt WHERE t.clientId = :clientId AND t.isRevoked = false",
    )
    fun revokeByClientId(
        @Param("clientId") clientId: String,
        @Param("revokedAt") revokedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "UPDATE OAuth2AccessTokenEntity t SET t.isRevoked = true, t.revokedAt = :revokedAt WHERE t.userId = :userId AND t.isRevoked = false",
    )
    fun revokeByUserId(
        @Param("userId") userId: Long,
        @Param("revokedAt") revokedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "UPDATE OAuth2AccessTokenEntity t SET t.isRevoked = true, t.revokedAt = :revokedAt WHERE t.refreshTokenId = :refreshTokenId AND t.isRevoked = false",
    )
    fun revokeByRefreshTokenId(
        @Param("refreshTokenId") refreshTokenId: String,
        @Param("revokedAt") revokedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query("DELETE FROM OAuth2AccessTokenEntity t WHERE t.expiresAt < :now AND t.isRevoked = true")
    fun deleteExpiredAndRevoked(@Param("now") now: LocalDateTime): Int
}