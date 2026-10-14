package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.EmailVerificationTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationTokenEntity, Long> {

    fun findByTokenHashAndIsVerifiedFalse(tokenHash: String): Optional<EmailVerificationTokenEntity>

    fun findByUserIdAndIsVerifiedFalseOrderByCreatedAtDesc(userId: Long): List<EmailVerificationTokenEntity>

    @Modifying
    @Query("UPDATE EmailVerificationTokenEntity t SET t.isVerified = true WHERE t.userId = :userId AND t.isVerified = false")
    fun invalidateAllByUserId(@Param("userId") userId: Long): Int

    @Modifying
    @Query("DELETE FROM EmailVerificationTokenEntity t WHERE t.expiresAt < :now")
    fun deleteExpiredTokens(@Param("now") now: LocalDateTime): Int
}