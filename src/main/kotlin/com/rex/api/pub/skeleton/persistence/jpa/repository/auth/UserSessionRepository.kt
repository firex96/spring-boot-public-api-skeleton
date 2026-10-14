package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface UserSessionRepository : JpaRepository<UserSessionEntity, Long> {
    fun findBySessionId(sessionId: String): Optional<UserSessionEntity>

    fun findBySessionIdAndIsRevokedFalse(sessionId: String): Optional<UserSessionEntity>

    fun findByUserIdAndIsRevokedFalse(userId: Long): List<UserSessionEntity>

    fun findByUserIdAndIsRevokedFalseOrderByLastActivityAtDesc(userId: Long): List<UserSessionEntity>

    fun findByUserId(userId: Long): List<UserSessionEntity>

    @Modifying
    @Query("UPDATE UserSessionEntity s SET s.isRevoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason WHERE s.sessionId = :sessionId")
    fun revokeSession(
        @Param("sessionId") sessionId: String,
        @Param("revokedAt") revokedAt: LocalDateTime,
        @Param("reason") reason: String,
    ): Int

    @Modifying
    @Query("UPDATE UserSessionEntity s SET s.isRevoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason WHERE s.userId = :userId AND s.isRevoked = false")
    fun revokeAllUserSessions(
        @Param("userId") userId: Long,
        @Param("revokedAt") revokedAt: LocalDateTime,
        @Param("reason") reason: String,
    ): Int

    @Modifying
    @Query(
        "UPDATE UserSessionEntity s SET s.isRevoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason WHERE s.userId = :userId AND s.sessionId != :excludeSessionId AND s.isRevoked = false",
    )
    fun revokeAllUserSessionsExcept(
        @Param("userId") userId: Long,
        @Param("excludeSessionId") excludeSessionId: String,
        @Param("revokedAt") revokedAt: LocalDateTime,
        @Param("reason") reason: String,
    ): Int

    @Modifying
    @Query("UPDATE UserSessionEntity s SET s.lastActivityAt = :lastActivityAt WHERE s.sessionId = :sessionId")
    fun updateLastActivity(
        @Param("sessionId") sessionId: String,
        @Param("lastActivityAt") lastActivityAt: LocalDateTime,
    ): Int

    fun countByUserIdAndIsRevokedFalse(userId: Long): Long
}