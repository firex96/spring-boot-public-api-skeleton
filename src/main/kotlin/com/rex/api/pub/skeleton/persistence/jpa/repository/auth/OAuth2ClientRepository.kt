package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2ClientEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface OAuth2ClientRepository : JpaRepository<OAuth2ClientEntity, Long> {
    fun findByClientId(clientId: String): Optional<OAuth2ClientEntity>

    fun findByClientIdAndIsActiveTrue(clientId: String): Optional<OAuth2ClientEntity>

    fun existsByClientId(clientId: String): Boolean

    fun findByIsActiveTrue(): List<OAuth2ClientEntity>

    fun findByIsFirstPartyTrue(): List<OAuth2ClientEntity>
}