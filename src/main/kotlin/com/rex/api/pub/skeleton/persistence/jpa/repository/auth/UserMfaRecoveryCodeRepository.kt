package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserMfaRecoveryCodeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UserMfaRecoveryCodeRepository : JpaRepository<UserMfaRecoveryCodeEntity, Long> {
    fun countByUserIdAndUsedAtIsNull(userId: Long): Long

    /** 복구 코드 사용 — 아직 쓰지 않은 같은 사용자의 코드일 때만 갱신한다. 갱신 행 수가 1 이어야 통과다(재사용 방지). */
    @Modifying(flushAutomatically = true, clearAutomatically = true) // 같은 트랜잭션의 엔티티가 낡은 값으로 덮어쓰지 않게
    @Query(
        "UPDATE UserMfaRecoveryCodeEntity r SET r.usedAt = :usedAt " +
            "WHERE r.userId = :userId AND r.codeHash = :codeHash AND r.usedAt IS NULL",
    )
    fun markUsed(
        @Param("userId") userId: Long,
        @Param("codeHash") codeHash: String,
        @Param("usedAt") usedAt: LocalDateTime,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true) // 같은 트랜잭션의 엔티티가 낡은 값으로 덮어쓰지 않게
    @Query("DELETE FROM UserMfaRecoveryCodeEntity r WHERE r.userId = :userId")
    fun deleteAllByUserIdInBulk(
        @Param("userId") userId: Long,
    ): Int
}