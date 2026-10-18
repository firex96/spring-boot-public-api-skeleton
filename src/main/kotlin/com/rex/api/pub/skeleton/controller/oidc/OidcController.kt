package com.rex.api.pub.skeleton.controller.oidc

import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oidc.OpenIdConfigurationModel
import com.rex.api.pub.skeleton.model.oidc.UserInfoModel
import com.rex.api.pub.skeleton.service.mapping.OidcMappingService
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@Tag(name = "OpenID Connect", description = "OIDC Discovery, JWKS, UserInfo 엔드포인트")
@RestController
class OidcController(
    private val mappingService: OidcMappingService,
) {
    @Operation(summary = "OpenID Connect Discovery", description = "OIDC 설정 정보 반환")
    @GetMapping("/.well-known/openid-configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getOpenIdConfiguration(): ResponseEntity<OpenIdConfigurationModel> =
        ResponseEntity.ok(mappingService.getOpenIdConfiguration())

    @Operation(summary = "JSON Web Key Set", description = "ID Token 검증을 위한 공개 키 반환")
    @GetMapping("/.well-known/jwks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getJwks(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mappingService.getJwkSet())

    @Operation(
        summary = "UserInfo 조회",
        description = "이 인가 서버가 발급한 access 토큰(openid scope)으로 사용자 정보 조회. 1st-party 로그인 토큰은 받지 않는다",
    )
    @GetMapping("/oauth2/userinfo", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getUserInfo(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<UserInfoModel> {
        val accessToken =
            authorization?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }?.substring(BEARER_PREFIX.length)?.trim()
                ?: throw OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_TOKEN, "Bearer 토큰이 필요합니다")
        return ResponseEntity.ok(mappingService.getUserInfo(accessToken))
    }

    /** RFC 6750 §3 — 토큰 불량 401·scope 부족 403, `WWW-Authenticate: Bearer error=…` 와 RFC 6749 형식 본문. */
    @ExceptionHandler(OAuth2Exception::class)
    fun handleOAuth2Exception(e: OAuth2Exception): ResponseEntity<OAuth2ErrorResponseModel> {
        val insufficientScope = e.error == OAuth2AuthorizationBizService.ERROR_INSUFFICIENT_SCOPE
        val challenge =
            if (insufficientScope) {
                "Bearer error=\"insufficient_scope\", scope=\"openid\""
            } else {
                "Bearer error=\"invalid_token\""
            }
        return ResponseEntity.status(if (insufficientScope) HttpStatus.FORBIDDEN else HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
            .body(mappingService.toErrorModel(e))
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}