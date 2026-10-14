package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PasswordResetTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, Long> {

    fun findByTokenHashAndIsUsedFalse(tokenHash: String): Optional<PasswordResetTokenEntity>

    fun findByUserIdAndIsUsedFalseOrderByCreatedAtDesc(userId: Long): List<PasswordResetTokenEntity>

    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.isUsed = true WHERE t.userId = :userId AND t.isUsed = false")
    fun invalidateAllByUserId(@Param("userId") userId: Long): Int

    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.expiresAt < :now")
    fun deleteExpiredTokens(@Param("now") now: LocalDateTime): Int
}