package com.rex.api.pub.skeleton.controller.auth

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.config.security.browser.BrowserSecurityPaths
import com.rex.api.pub.skeleton.config.security.browser.BrowserSessionAuthenticator
import com.rex.api.pub.skeleton.config.security.browser.MfaPendingTokenStore
import com.rex.api.pub.skeleton.controller.auth.support.toResponseEntity
import com.rex.api.pub.skeleton.dto.auth.SocialLoginRequestDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.model.auth.BrowserLoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.SocialAccountModel
import com.rex.api.pub.skeleton.service.mapping.SocialAuthMappingService
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Tag(name = "Social Auth", description = "소셜 로그인 API")
@RestController
@RequestMapping("/auth/oauth2")
class SocialAuthController(
    private val socialAuthMappingService: SocialAuthMappingService,
    private val clientIpResolver: ClientIpResolver,
    private val browserSessionAuthenticator: BrowserSessionAuthenticator,
    private val mfaPendingTokenStore: MfaPendingTokenStore,
) {
    @Operation(
        summary = "소셜 로그인 시작",
        description = "브라우저 세션에 묶인 state 를 발급하고 provider 인가 화면으로 302 한다. 브라우저가 이 경로로 직접 이동한다",
    )
    @GetMapping("/{provider}/authorize")
    fun authorize(
        @PathVariable provider: SocialProvider,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        httpResponse.sendRedirect(socialAuthMappingService.issueAuthorizationRedirect(provider, httpRequest.getSession(true).id))
    }

    @Operation(summary = "소셜 로그인 콜백 (리다이렉트)", description = "OAuth 인증 후 콜백 처리 - 프론트엔드로 리다이렉트")
    @GetMapping("/{provider}/callback")
    fun handleCallback(
        @PathVariable provider: SocialProvider,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(name = "error_description", required = false) errorDescription: String?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        // state 는 무엇보다 먼저 소비한다 — 이 브라우저 세션이 시작한 인가가 아니면 provider 오류든 코드든 처리하지 않는다(로그인 CSRF 차단)
        val browserSessionId = httpRequest.getSession(false)?.id ?: ""
        if (!socialAuthMappingService.consumeState(provider, browserSessionId, state)) {
            redirectToCallbackError(httpResponse, "invalid_state", "State does not match this browser session")
            return
        }

        // 에러 처리
        if (error != null) {
            redirectToCallbackError(httpResponse, error, errorDescription ?: "")
            return
        }

        // code가 없는 경우 에러 처리
        if (code.isNullOrBlank()) {
            redirectToCallbackError(httpResponse, "missing_code", "Authorization code is missing")
            return
        }

        // 로그인 처리 — 토큰은 URL 에 싣지 않는다. 서버에 맡기고(교환 대기) 브라우저 세션에 로그인시킨 뒤 쿼리 없이 리다이렉트한다
        val outcome =
            socialAuthMappingService.socialLoginForBrowser(
                provider = provider,
                code = code,
                state = state,
                deviceInfo = httpRequest.getHeader("User-Agent"),
                ipAddress = clientIpResolver.resolve(httpRequest),
            )
        when (outcome) {
            is BrowserLoginOutcomeModel.SignedIn -> {
                browserSessionAuthenticator.signIn(httpRequest, httpResponse, outcome.principal)
                httpResponse.sendRedirect(BrowserSecurityPaths.DEMO_CALLBACK)
            }
            // 2차 인증 — 세션에는 challenge 토큰만 두고(로그인시키지 않는다) 세션 ID 를 바꿔 2단계 화면으로
            is BrowserLoginOutcomeModel.MfaRequired -> {
                mfaPendingTokenStore.begin(httpRequest, outcome.mfaToken)
                httpResponse.sendRedirect(BrowserSecurityPaths.AUTH_MFA)
            }
        }
    }

    /**
     * 네이티브 클라이언트가 provider SDK 로 직접 받은 코드를 교환한다. 서버가 state 를 발급하지 않은 흐름이므로
     * state 검증은 그 클라이언트의 책임이다 — 브라우저 흐름은 `authorize` → `callback` 을 쓴다.
     */
    @Operation(summary = "소셜 로그인 (코드 교환)", description = "Authorization code를 토큰으로 교환합니다")
    @PostMapping("/{provider}/login")
    fun socialLogin(
        @PathVariable provider: SocialProvider,
        @Valid @RequestBody request: SocialLoginRequestDto,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        val outcome =
            socialAuthMappingService.socialLogin(
                provider = provider,
                code = request.code,
                deviceType = request.deviceType,
                deviceInfo = request.deviceInfo,
                ipAddress = clientIpResolver.resolve(httpRequest),
            )
        // 2차 인증을 켠 계정은 202 challenge — POST /auth/login/mfa 로 마친다
        return outcome.toResponseEntity()
    }

    @Operation(summary = "소셜 계정 연동", description = "기존 계정에 소셜 계정을 연동합니다")
    @PostMapping("/{provider}/link")
    fun linkSocialAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable provider: SocialProvider,
        @Valid @RequestBody request: SocialLoginRequestDto,
    ): ResponseEntity<SocialAccountModel> {
        val socialAccount =
            socialAuthMappingService.linkSocialAccount(
                userId = user.userId,
                provider = provider,
                code = request.code,
                ipAddress = null,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(socialAccount)
    }

    @Operation(summary = "소셜 계정 연동 해제", description = "연동된 소셜 계정을 해제합니다")
    @DeleteMapping("/{provider}/unlink")
    fun unlinkSocialAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable provider: SocialProvider,
    ): ResponseEntity<Void> {
        socialAuthMappingService.unlinkSocialAccount(user.userId, provider, null)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "연동된 소셜 계정 목록 조회")
    @GetMapping("/accounts")
    fun getSocialAccounts(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<SocialAccountModel>> {
        val accounts = socialAuthMappingService.getSocialAccounts(user.userId)
        return ResponseEntity.ok(accounts)
    }

    /** 데모 콜백으로 오류 리다이렉트 — 값 하나에 인코딩 한 번(provider 가 준 &·#·공백이 쿼리 구분자로 새지 않게). */
    private fun redirectToCallbackError(response: HttpServletResponse, error: String, description: String) {
        val encodedError = URLEncoder.encode(error, StandardCharsets.UTF_8)
        val encodedDescription = URLEncoder.encode(description, StandardCharsets.UTF_8)
        response.sendRedirect("${BrowserSecurityPaths.DEMO_CALLBACK}?error=$encodedError&error_description=$encodedDescription")
    }
}