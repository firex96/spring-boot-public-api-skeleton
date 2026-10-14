package com.rex.api.pub.skeleton.persistence.jpa.repository.auth.security

import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.security.BlockedIpEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface BlockedIpRepository : JpaRepository<BlockedIpEntity, Long> {

    fun findByIpAddressAndIsActiveTrue(ipAddress: String): Optional<BlockedIpEntity>

    fun findByIsActiveTrue(pageable: Pageable): Page<BlockedIpEntity>

    fun findByBlockReason(blockReason: BlockReason, pageable: Pageable): Page<BlockedIpEntity>

    @Query(
        """
        SELECT b FROM BlockedIpEntity b
        WHERE b.ipAddress = :ipAddress
        AND b.isActive = true
        AND (b.expiresAt IS NULL OR b.expiresAt > :now)
        """,
    )
    fun findActiveBlock(
        @Param("ipAddress") ipAddress: String,
        @Param("now") now: LocalDateTime,
    ): Optional<BlockedIpEntity>

    @Query("SELECT COUNT(b) > 0 FROM BlockedIpEntity b WHERE b.ipAddress = :ipAddress AND b.isActive = true AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
    fun isBlocked(
        @Param("ipAddress") ipAddress: String,
        @Param("now") now: LocalDateTime,
    ): Boolean

    @Modifying
    @Query("UPDATE BlockedIpEntity b SET b.isActive = false, b.unblockedAt = :now WHERE b.expiresAt <= :now AND b.isActive = true")
    fun deactivateExpiredBlocks(@Param("now") now: LocalDateTime): Int
}