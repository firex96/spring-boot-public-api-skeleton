package com.rex.api.pub.skeleton.service.oidc

import com.rex.api.pub.skeleton.dto.oidc.UserInfoDto
import com.rex.api.pub.skeleton.exception.ItemNotFoundException
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
) {
    fun getUserInfo(userId: Long): UserInfoDto {
        val user = userEntityService.getUserById(userId) ?: throw ItemNotFoundException("사용자를 찾을 수 없습니다")
        return UserInfoDto(
            sub = user.id.toString(),
            name = user.name,
            nickname = user.nickname,
            picture = user.profileImage,
            email = user.email,
            emailVerified = user.email != null,
            phoneNumber = user.phone,
            phoneNumberVerified = false,
            updatedAt = user.updatedAt.toEpochSecond(ZoneOffset.UTC),
        )
    }

    fun getJwkSet(): Map<String, Any> = jwkBizService.getJwkSet()
}