package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserMfaCredentialEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserMfaCredentialRepository : JpaRepository<UserMfaCredentialEntity, Long> {
    fun findByUserId(userId: Long): Optional<UserMfaCredentialEntity>

    /** 등록을 바꾸는 작업(setup·activate·disable 등)이 사용자 단위로 직렬화되도록 행을 잡는다(SELECT … FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM UserMfaCredentialEntity c WHERE c.userId = :userId")
    fun findByUserIdForUpdate(
        @Param("userId") userId: Long,
    ): Optional<UserMfaCredentialEntity>

    /**
     * 로그인 관문의 활성 판정 — 공유 잠금(SELECT … FOR SHARE)으로 읽어, 진행 중인 활성화와 순서를 맞춘다.
     * 로그인이 먼저 잡으면 활성화는 로그인 트랜잭션(세션 발급)이 끝난 뒤 그 세션까지 폐기하고, 활성화가 먼저면 로그인은 ACTIVE 를 본다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c FROM UserMfaCredentialEntity c WHERE c.userId = :userId")
    fun findByUserIdForShare(
        @Param("userId") userId: Long,
    ): Optional<UserMfaCredentialEntity>

    /**
     * TOTP 구간 사용 표시 — 마지막 사용 구간보다 **뒤의** 구간일 때만 갱신한다. 갱신 행 수가 1 이어야 통과다.
     * 한 문장이라 동시 요청 중 하나만 1 을 받는다(같은 구간 코드 재사용 방지).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true) // 같은 트랜잭션의 엔티티가 낡은 값으로 덮어쓰지 않게
    @Query(
        "UPDATE UserMfaCredentialEntity c SET c.lastUsedStep = :step, c.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE c.userId = :userId AND (c.lastUsedStep IS NULL OR c.lastUsedStep < :step)",
    )
    fun markStepUsed(
        @Param("userId") userId: Long,
        @Param("step") step: Long,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true) // 같은 트랜잭션의 엔티티가 낡은 값으로 덮어쓰지 않게
    @Query("DELETE FROM UserMfaCredentialEntity c WHERE c.userId = :userId")
    fun deleteAllByUserIdInBulk(
        @Param("userId") userId: Long,
    ): Int
}