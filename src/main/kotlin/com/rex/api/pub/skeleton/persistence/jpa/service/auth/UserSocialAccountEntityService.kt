package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserSocialAccountDto
import com.rex.api.pub.skeleton.mapper.SocialAuthMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSocialAccountEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserSocialAccountRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/** 소셜 연동 계정 엔티티 서비스. 조회는 `UserSocialAccountDto`(토큰 포함 내부용), 변경은 의도 메서드. */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserSocialAccountEntityService(
    private val userSocialAccountRepository: UserSocialAccountRepository,
    private val mapper: SocialAuthMapper,
) {
    fun getByProviderAndProviderId(provider: String, providerId: String): UserSocialAccountDto? =
        userSocialAccountRepository.findByProviderAndProviderId(provider, providerId).getOrNull()?.let { mapper.toUserSocialAccountDto(it) }

    fun getByUserIdAndProvider(userId: Long, provider: String): UserSocialAccountDto? =
        userSocialAccountRepository.findByUserIdAndProvider(userId, provider).getOrNull()?.let { mapper.toUserSocialAccountDto(it) }

    fun getAllByUserId(userId: Long): List<UserSocialAccountDto> =
        mapper.toUserSocialAccountDtoList(userSocialAccountRepository.findByUserId(userId))

    @Transactional(transactionManager = "authTransactionManager")
    fun link(
        userId: Long,
        provider: String,
        providerId: String,
        email: String?,
        name: String?,
        profileImage: String?,
        accessToken: String?,
        refreshToken: String?,
        tokenExpiresAt: LocalDateTime?,
    ): UserSocialAccountDto {
        val account =
            UserSocialAccountEntity(
                userId = userId,
                provider = provider,
                providerId = providerId,
                email = email,
                name = name,
                profileImage = profileImage,
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiresAt = tokenExpiresAt,
            )
        return mapper.toUserSocialAccountDto(userSocialAccountRepository.save(account))
    }

    /** 로그인마다 provider 가 준 최신 값으로 전부 덮는다(null 도 그대로) — 기존 동작. */
    @Transactional(transactionManager = "authTransactionManager")
    fun updateTokensAndProfile(
        id: Long,
        accessToken: String?,
        refreshToken: String?,
        tokenExpiresAt: LocalDateTime?,
        email: String?,
        name: String?,
        profileImage: String?,
    ) {
        val account = userSocialAccountRepository.findByIdOrNull(id) ?: throw NoSuchElementException("소셜 계정이 없습니다: $id")
        account.accessToken = accessToken
        account.refreshToken = refreshToken
        account.tokenExpiresAt = tokenExpiresAt
        account.email = email
        account.name = name
        account.profileImage = profileImage
        account.updatedAt = LocalDateTime.now()
        userSocialAccountRepository.save(account)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun unlink(id: Long) {
        userSocialAccountRepository.deleteById(id)
    }
}