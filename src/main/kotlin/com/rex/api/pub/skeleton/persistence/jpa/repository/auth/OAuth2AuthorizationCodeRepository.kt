package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AuthorizationCodeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface OAuth2AuthorizationCodeRepository : JpaRepository<OAuth2AuthorizationCodeEntity, Long> {
    fun findByCode(code: String): Optional<OAuth2AuthorizationCodeEntity>

    fun findByCodeAndIsUsedFalse(code: String): Optional<OAuth2AuthorizationCodeEntity>

    @Modifying
    @Query("UPDATE OAuth2AuthorizationCodeEntity c SET c.isUsed = true, c.usedAt = :usedAt WHERE c.code = :code")
    fun markAsUsed(
        @Param("code") code: String,
        @Param("usedAt") usedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query("DELETE FROM OAuth2AuthorizationCodeEntity c WHERE c.expiresAt < :now OR c.isUsed = true")
    fun deleteExpiredOrUsed(@Param("now") now: LocalDateTime): Int

    fun deleteByClientId(clientId: String)
}