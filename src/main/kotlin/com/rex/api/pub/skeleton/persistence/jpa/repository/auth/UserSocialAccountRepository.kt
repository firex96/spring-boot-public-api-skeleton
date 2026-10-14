package com.rex.api.pub.skeleton.persistence.jpa.repository.auth

import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSocialAccountEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserSocialAccountRepository : JpaRepository<UserSocialAccountEntity, Long> {
    fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): Optional<UserSocialAccountEntity>

    fun findByUserId(userId: Long): List<UserSocialAccountEntity>

    fun findByUserIdAndProvider(
        userId: Long,
        provider: String,
    ): Optional<UserSocialAccountEntity>

    fun existsByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): Boolean

    fun deleteByUserIdAndProvider(
        userId: Long,
        provider: String,
    )
}