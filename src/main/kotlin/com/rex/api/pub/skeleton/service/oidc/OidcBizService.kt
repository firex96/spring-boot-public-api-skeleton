package com.rex.api.pub.skeleton.service.oidc

import com.rex.api.pub.skeleton.dto.oidc.UserInfoDto
import com.rex.api.pub.skeleton.exception.ItemNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

/**
 * OIDC 비즈니스 서비스 — userinfo 클레임 조립과 JWK Set. `OidcMappingService` 가 EntityService·`JwkBizService` 를 직접 부르던 것을 이 계층으로 내렸다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class OidcBizService(
    private val userEntityService: UserEntityService,
    private val jwkBizService: JwkBizService,
    private val tokenEntityService: TokenEntityService,
) {
    /**
     * userinfo 클레임 — 토큰이 받은 scope 만큼만 싣는다(OIDC Core §5.4). `profile` → name·nickname·picture·updated_at,
     * `email` → email·email_verified. 그 밖은 null 이고 응답에서 빠진다.
     */
    fun getUserInfo(userId: Long, scopes: Set<String>): UserInfoDto {
        val user = userEntityService.getUserById(userId) ?: throw ItemNotFoundException("사용자를 찾을 수 없습니다")
        val profile = SCOPE_PROFILE in scopes
        val email = SCOPE_EMAIL in scopes
        return UserInfoDto(
            sub = user.id.toString(),
            name = user.name.takeIf { profile },
            nickname = user.nickname.takeIf { profile },
            picture = user.profileImage.takeIf { profile },
            email = user.email.takeIf { email },
            emailVerified = if (email && user.email != null) tokenEntityService.existsVerifiedEmail(user.id, user.email) else null,
            updatedAt = if (profile) user.updatedAt.toEpochSecond(ZoneOffset.UTC) else null,
        )
    }

    fun getJwkSet(): Map<String, Any> = jwkBizService.getJwkSet()

    companion object {
        private const val SCOPE_PROFILE = "profile"
        private const val SCOPE_EMAIL = "email"
    }
}