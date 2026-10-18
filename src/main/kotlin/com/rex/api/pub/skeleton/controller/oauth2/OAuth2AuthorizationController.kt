package com.rex.api.pub.skeleton.controller.oauth2

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.config.security.browser.AuthorizationRequestStore
import com.rex.api.pub.skeleton.controller.oauth2.support.AuthorizeRedirectUrlBuilder
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oauth2.OidcTokenResponseModel
import com.rex.api.pub.skeleton.model.oauth2.TokenIntrospectionResponseModel
import com.rex.api.pub.skeleton.service.mapping.OAuth2AuthorizationMappingService
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * OAuth2 인가 서버 엔드포인트. 판정(로그인 필요·동의 필요·코드 발급·오류)은 MappingService → BizService 가 내리고,
 * 이 컨트롤러는 리다이렉트(`AuthorizeRedirectUrlBuilder`)와 응답 상태 매핑만 한다. 로그인·동의 화면은 `OAuth2BrowserController`.
 */
@Tag(name = "OAuth2 Authorization Server", description = "OAuth2 인증 서버 API")
@RestController
@RequestMapping("/oauth2")
class OAuth2AuthorizationController(
    private val oAuth2AuthorizationMappingService: OAuth2AuthorizationMappingService,
    private val authorizationRequestStore: AuthorizationRequestStore,
) {
    // ===== Authorization Endpoint =====

    @Operation(summary = "Authorization 요청", description = "OAuth2 Authorization Code Flow 시작점")
    @GetMapping("/authorize")
    fun authorize(
        @RequestParam("response_type") responseType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("nonce", required = false) nonce: String?,
        @AuthenticationPrincipal user: AuthenticatedUser?,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val request =
            AuthorizationRequestDto(
                responseType = responseType,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
                state = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                nonce = nonce,
            )
        // RFC 6749 §4.1.2.1 — client·redirect_uri 가 잘못되면 리다이렉트하지 않는다. 그 실패는 예외로 올라와 @ExceptionHandler 가 JSON(401/400)으로 끝낸다
        // user 는 브라우저 세션의 principal 이다 — 이 경로는 브라우저 체인이라 Bearer 를 읽지 않는다
        val decision = oAuth2AuthorizationMappingService.authorize(request, user?.userId)
        if (decision.nextStep == AuthorizeNextStep.LOGIN_REQUIRED) {
            // 돌아올 곳은 서버가 검증된 파라미터로 다시 조립해 세션에 둔다 — 쿼리로 받은 복귀 주소를 쓰지 않는다
            authorizationRequestStore.save(httpRequest, AuthorizeRedirectUrlBuilder.authorizeUrl(request))
        }
        response.sendRedirect(AuthorizeRedirectUrlBuilder.byDecision(decision, request))
    }

    // ===== Token Endpoint =====

    @Operation(summary = "토큰 발급", description = "Access Token 발급 (Authorization Code, Refresh Token, Client Credentials)")
    @PostMapping("/token", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun token(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("client_secret", required = false) clientSecret: String?,
        @RequestParam("refresh_token", required = false) refreshToken: String?,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("code_verifier", required = false) codeVerifier: String?,
    ): ResponseEntity<OidcTokenResponseModel> {
        val authenticatedClientId = oAuth2AuthorizationMappingService.authenticateClient(clientId, clientSecret)
        val response =
            oAuth2AuthorizationMappingService.token(
                grantType = grantType,
                code = code,
                redirectUri = redirectUri,
                clientId = authenticatedClientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken,
                scope = scope,
                codeVerifier = codeVerifier,
            )
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "토큰 검증", description = "Access Token 또는 Refresh Token 유효성 검증")
    @PostMapping("/introspect", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun introspect(
        @RequestParam("token") token: String,
        @RequestParam("token_type_hint", required = false) tokenTypeHint: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("client_secret", required = false) clientSecret: String?,
    ): ResponseEntity<TokenIntrospectionResponseModel> {
        val authenticatedClientId = oAuth2AuthorizationMappingService.authenticateClient(clientId, clientSecret)
        return ResponseEntity.ok(oAuth2AuthorizationMappingService.introspect(token, authenticatedClientId))
    }

    @Operation(summary = "토큰 취소", description = "Access Token 또는 Refresh Token 취소")
    @PostMapping("/revoke", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun revoke(
        @RequestParam("token") token: String,
        @RequestParam("token_type_hint", required = false) tokenTypeHint: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("client_secret", required = false) clientSecret: String?,
    ): ResponseEntity<Void> {
        val authenticatedClientId = oAuth2AuthorizationMappingService.authenticateClient(clientId, clientSecret)
        oAuth2AuthorizationMappingService.revoke(token, authenticatedClientId)
        return ResponseEntity.ok().build()
    }

    // ===== Exception Handler =====

    @ExceptionHandler(OAuth2Exception::class)
    fun handleOAuth2Exception(e: OAuth2Exception): ResponseEntity<OAuth2ErrorResponseModel> {
        val status =
            when (e.error) {
                OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT -> HttpStatus.UNAUTHORIZED
                OAuth2AuthorizationBizService.ERROR_ACCESS_DENIED -> HttpStatus.FORBIDDEN
                else -> HttpStatus.BAD_REQUEST
            }
        return ResponseEntity.status(status).body(oAuth2AuthorizationMappingService.toErrorModel(e))
    }
}