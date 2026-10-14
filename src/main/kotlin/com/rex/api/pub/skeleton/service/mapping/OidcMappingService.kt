package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.config.security.OidcProperties
import com.rex.api.pub.skeleton.mapper.OidcMapper
import com.rex.api.pub.skeleton.model.oidc.OpenIdConfigurationModel
import com.rex.api.pub.skeleton.model.oidc.UserInfoModel
import com.rex.api.pub.skeleton.service.oidc.OidcBizService
import org.springframework.stereotype.Service

/**
 * OIDC 매핑 서비스
 * DTO ↔ Model 변환 및 BizService 호출 담당
 */
@Service
class OidcMappingService(
    private val oidcProperties: OidcProperties,
    private val oidcBizService: OidcBizService,
    private val mapper: OidcMapper,
) {
    fun getOpenIdConfiguration(): OpenIdConfigurationModel {
        val baseUrl = oidcProperties.issuer

        return OpenIdConfigurationModel(
            issuer = baseUrl,
            authorizationEndpoint = "$baseUrl/oauth2/authorize",
            tokenEndpoint = "$baseUrl/oauth2/token",
            userinfoEndpoint = "$baseUrl/oauth2/userinfo",
            jwksUri = "$baseUrl/.well-known/jwks.json",
            revocationEndpoint = "$baseUrl/oauth2/revoke",
            introspectionEndpoint = "$baseUrl/oauth2/introspect",
        )
    }

    fun getJwkSet(): Map<String, Any> = oidcBizService.getJwkSet()

    fun getUserInfo(userId: Long): UserInfoModel = mapper.toUserInfoModel(oidcBizService.getUserInfo(userId))
}