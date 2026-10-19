package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.mapper.OAuth2Mapper
import com.rex.api.pub.skeleton.model.oauth2.AuthorizationConsentModel
import com.rex.api.pub.skeleton.model.oauth2.AuthorizeDecisionModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oauth2.OidcTokenResponseModel
import com.rex.api.pub.skeleton.model.oauth2.TokenIntrospectionResponseModel
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import org.springframework.stereotype.Service

/** OAuth2 인가 서버 API 매핑 — `OAuth2AuthorizationController`. 판정·발급은 BizService, 여기서는 Model 변환만. */
@Service
class OAuth2AuthorizationMappingService(
    private val oAuth2AuthorizationBizService: OAuth2AuthorizationBizService,
    private val mapper: OAuth2Mapper,
) {
    /** [sessionId] 는 브라우저 principal 의 사용자 세션 — 인가 코드의 `auth_time` 을 여기서 읽는다. */
    fun authorize(request: AuthorizationRequestDto, userId: Long?, sessionId: String?): AuthorizeDecisionModel =
        mapper.toAuthorizeDecisionModel(oAuth2AuthorizationBizService.authorize(request, userId, sessionId))

    fun consent(request: AuthorizationRequestDto, userId: Long, sessionId: String, approve: Boolean): AuthorizeDecisionModel =
        mapper.toAuthorizeDecisionModel(oAuth2AuthorizationBizService.consent(request, userId, sessionId, approve))

    fun getConsentInfo(request: AuthorizationRequestDto): AuthorizationConsentModel {
        val client = oAuth2AuthorizationBizService.validateAuthorizationRequest(request)
        return mapper.toAuthorizationConsentModel(oAuth2AuthorizationBizService.getConsentInfo(request, client))
    }

    fun authenticateClient(clientId: String?, clientSecret: String?): String = oAuth2AuthorizationBizService.authenticateClient(clientId, clientSecret)

    fun token(
        grantType: String,
        code: String?,
        redirectUri: String?,
        clientId: String,
        clientSecret: String?,
        refreshToken: String?,
        scope: String?,
        codeVerifier: String?,
    ): OidcTokenResponseModel =
        mapper.toOidcTokenResponseModel(
            oAuth2AuthorizationBizService.issueTokenByGrant(
                grantType = grantType,
                code = code,
                redirectUri = redirectUri,
                clientId = clientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken,
                scope = scope,
                codeVerifier = codeVerifier,
            ),
        )

    fun introspect(token: String, clientId: String): TokenIntrospectionResponseModel =
        mapper.toTokenIntrospectionResponseModel(oAuth2AuthorizationBizService.introspectToken(token, clientId))

    fun revoke(token: String, clientId: String) {
        oAuth2AuthorizationBizService.revokeToken(token, clientId)
    }

    fun toErrorModel(e: OAuth2Exception): OAuth2ErrorResponseModel =
        mapper.toErrorResponseModel(e)
}