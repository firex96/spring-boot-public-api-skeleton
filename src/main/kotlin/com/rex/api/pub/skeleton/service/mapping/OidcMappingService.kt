package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.config.security.OidcProperties
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.mapper.OAuth2Mapper
import com.rex.api.pub.skeleton.mapper.OidcMapper
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oidc.OpenIdConfigurationModel
import com.rex.api.pub.skeleton.model.oidc.UserInfoModel
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
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
    private val oAuth2AuthorizationBizService: OAuth2AuthorizationBizService,
    private val mapper: OidcMapper,
    private val oAuth2Mapper: OAuth2Mapper,
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

    fun toErrorModel(e: OAuth2Exception): OAuth2ErrorResponseModel =
        oAuth2Mapper.toErrorResponseModel(e)

    /** userinfo — Bearer 가 이 인가 서버의 access 토큰인지, openid scope 를 받았는지 먼저 본다(실패는 `OAuth2Exception`). */
    fun getUserInfo(accessToken: String): UserInfoModel {
        val grant = oAuth2AuthorizationBizService.resolveUserInfoGrant(accessToken)
        return mapper.toUserInfoModel(oidcBizService.getUserInfo(grant.userId, grant.scopes))
    }
}