package com.rex.api.pub.skeleton.controller.oauth2

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.model.oauth2.AuthorizationConsentModel
import com.rex.api.pub.skeleton.model.oauth2.AuthorizeDecisionModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oauth2.OidcTokenResponseModel
import com.rex.api.pub.skeleton.model.oauth2.TokenIntrospectionResponseModel
import com.rex.api.pub.skeleton.service.mapping.OAuth2AuthorizationMappingService
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * OAuth2 인가 서버 엔드포인트. 판정(로그인 필요·동의 필요·코드 발급·오류)은 MappingService → BizService 가 내리고,
 * 이 컨트롤러는 리다이렉트 URL 조립과 응답 상태 매핑만 한다.
 */
@Tag(name = "OAuth2 Authorization Server", description = "OAuth2 인증 서버 API")
@RestController
@RequestMapping("/oauth2")
class OAuth2AuthorizationController(
    private val oAuth2AuthorizationMappingService: OAuth2AuthorizationMappingService,
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
        val decision = oAuth2AuthorizationMappingService.authorize(request, user?.userId)
        redirectByDecision(decision, request, response)
    }

    @Operation(summary = "동의 정보 조회", description = "사용자 동의 화면에 표시할 정보")
    @GetMapping("/consent")
    fun getConsentInfo(
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("nonce", required = false) nonce: String?,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<AuthorizationConsentModel> {
        val request =
            AuthorizationRequestDto(
                responseType = "code",
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
                state = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                nonce = nonce,
            )
        return ResponseEntity.ok(oAuth2AuthorizationMappingService.getConsentInfo(request))
    }

    @Operation(summary = "동의 처리", description = "사용자가 동의 버튼을 클릭했을 때")
    @PostMapping("/consent")
    fun handleConsent(
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("nonce", required = false) nonce: String?,
        @RequestParam("approve") approve: Boolean,
        @AuthenticationPrincipal user: AuthenticatedUser,
        response: HttpServletResponse,
    ) {
        val request =
            AuthorizationRequestDto(
                responseType = "code",
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
                state = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                nonce = nonce,
            )
        val decision = oAuth2AuthorizationMappingService.consent(request, user.userId, approve)
        redirectByDecision(decision, request, response)
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

    // ===== Private =====

    /** 판정 → 리다이렉트. redirect_uri 는 BizService 가 검증한 뒤라 신뢰한다(RFC 6749 §4.1.2.1). */
    private fun redirectByDecision(decision: AuthorizeDecisionModel, request: AuthorizationRequestDto, response: HttpServletResponse) {
        val url =
            when (decision.nextStep) {
                AuthorizeNextStep.LOGIN_REQUIRED -> buildLoginRedirectUrl(request)
                AuthorizeNextStep.CONSENT_REQUIRED -> buildConsentRedirectUrl(request)
                AuthorizeNextStep.CODE_ISSUED ->
                    buildRedirectUrlWithCode(request.redirectUri, requireNotNull(decision.code) { "CODE_ISSUED 판정에는 code 가 있다" }, request.state)
                AuthorizeNextStep.ERROR ->
                    buildErrorRedirectUrl(
                        request.redirectUri,
                        requireNotNull(decision.error) { "ERROR 판정에는 error 가 있다" },
                        decision.errorDescription,
                        request.state,
                    )
            }
        response.sendRedirect(url)
    }

    private fun buildLoginRedirectUrl(request: AuthorizationRequestDto): String {
        val params = mutableListOf<String>()
        params.add("redirect_to=/oauth2/authorize")
        params.add("response_type=${encode(request.responseType)}")
        params.add("client_id=${encode(request.clientId)}")
        params.add("redirect_uri=${encode(request.redirectUri)}")
        request.scope?.let { params.add("scope=${encode(it)}") }
        request.state?.let { params.add("state=${encode(it)}") }
        request.codeChallenge?.let { params.add("code_challenge=${encode(it)}") }
        request.codeChallengeMethod?.let { params.add("code_challenge_method=${encode(it)}") }
        request.nonce?.let { params.add("nonce=${encode(it)}") }

        return "/auth/login?${params.joinToString("&")}"
    }

    private fun buildConsentRedirectUrl(request: AuthorizationRequestDto): String {
        val params = mutableListOf<String>()
        params.add("client_id=${encode(request.clientId)}")
        params.add("redirect_uri=${encode(request.redirectUri)}")
        request.scope?.let { params.add("scope=${encode(it)}") }
        request.state?.let { params.add("state=${encode(it)}") }
        request.codeChallenge?.let { params.add("code_challenge=${encode(it)}") }
        request.codeChallengeMethod?.let { params.add("code_challenge_method=${encode(it)}") }
        request.nonce?.let { params.add("nonce=${encode(it)}") }

        return "/oauth2/consent?${params.joinToString("&")}"
    }

    /** 검증된 redirect_uri 만 받는다(RFC 6749 §4.1.2.1) — 호출자가 `validateClientAndRedirectUri` 를 지난 뒤여야 한다. */
    private fun buildRedirectUrlWithCode(redirectUri: String, code: String, state: String?): String {
        val params = mutableListOf("code=${encode(code)}")
        state?.let { params.add("state=${encode(it)}") }

        val separator = if (redirectUri.contains("?")) "&" else "?"
        return "$redirectUri$separator${params.joinToString("&")}"
    }

    /** 검증된 redirect_uri 만 받는다(RFC 6749 §4.1.2.1). */
    private fun buildErrorRedirectUrl(
        redirectUri: String,
        error: String,
        errorDescription: String?,
        state: String?,
    ): String {
        val params = mutableListOf("error=${encode(error)}")
        errorDescription?.let { params.add("error_description=${encode(it)}") }
        state?.let { params.add("state=${encode(it)}") }

        val separator = if (redirectUri.contains("?")) "&" else "?"
        return "$redirectUri$separator${params.joinToString("&")}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}