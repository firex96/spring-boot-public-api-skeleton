package com.rex.api.pub.skeleton.controller.oauth2

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.config.security.browser.AuthorizationRequestStore
import com.rex.api.pub.skeleton.config.security.browser.BrowserSecurityPaths
import com.rex.api.pub.skeleton.config.security.browser.BrowserSessionAuthenticator
import com.rex.api.pub.skeleton.controller.oauth2.support.AuthorizeRedirectUrlBuilder
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import com.rex.api.pub.skeleton.service.mapping.OAuth2AuthorizationMappingService
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 인가 서버의 브라우저 화면 — 로그인과 동의. 브라우저 체인(세션 쿠키 + CSRF 폼)을 탄다.
 *
 * authorize 가 `LOGIN_REQUIRED` 면 원래 요청을 세션에 두고 여기 로그인 화면으로, `CONSENT_REQUIRED` 면 동의 화면으로 보낸다.
 * 화면의 OAuth2 검증 실패는 HTML 오류 화면이다 — 클라이언트·redirect_uri 를 믿을 수 없으니 리다이렉트하지 않는다(RFC 6749 §4.1.2.1).
 */
@Tag(name = "OAuth2 Browser", description = "인가 서버 로그인·동의 화면")
@Controller
@RequestMapping("/oauth2")
class OAuth2BrowserController(
    private val oAuth2AuthorizationMappingService: OAuth2AuthorizationMappingService,
    private val authMappingService: AuthMappingService,
    private val clientIpResolver: ClientIpResolver,
    private val browserSessionAuthenticator: BrowserSessionAuthenticator,
    private val authorizationRequestStore: AuthorizationRequestStore,
) {
    @Operation(summary = "로그인 화면")
    @GetMapping("/login")
    fun loginPage(): String = VIEW_LOGIN

    @Operation(summary = "로그인", description = "성공하면 기다리던 authorize 요청으로 302. 실패하면 같은 화면에 메시지")
    @PostMapping("/login")
    fun login(
        @RequestParam("loginId") loginId: String,
        @RequestParam("password") password: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
    ): String {
        val principal =
            try {
                authMappingService.loginForBrowser(
                    LoginRequestDto(loginId = loginId, password = password, deviceType = DEVICE_TYPE, deviceInfo = request.getHeader("User-Agent")),
                    clientIpResolver.resolve(request),
                )
            } catch (e: AuthenticationException) {
                return loginFailed(model, e.message)
            } catch (e: AccountLockedException) {
                return loginFailed(model, e.message)
            } catch (e: ForbiddenException) {
                return loginFailed(model, e.message)
            }
        browserSessionAuthenticator.signIn(request, response, principal)
        return "redirect:" + (authorizationRequestStore.take(request) ?: "${BrowserSecurityPaths.OAUTH2_LOGIN}?success")
    }

    @Operation(summary = "동의 화면", description = "클라이언트 이름과 요청 scope 를 보여 준다")
    @GetMapping("/consent")
    fun consentPage(
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("nonce", required = false) nonce: String?,
        model: Model,
    ): String {
        val authorizationRequest = consentRequest(clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, nonce)
        model.addAttribute("consent", oAuth2AuthorizationMappingService.getConsentInfo(authorizationRequest))
        model.addAttribute("request", authorizationRequest)
        return VIEW_CONSENT
    }

    @Operation(summary = "동의 처리", description = "승인하면 redirect_uri?code&state, 거부하면 redirect_uri?error=access_denied&state")
    @PostMapping("/consent")
    fun consent(
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("nonce", required = false) nonce: String?,
        @RequestParam("approve") approve: Boolean,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): String {
        val authorizationRequest = consentRequest(clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, nonce)
        val decision = oAuth2AuthorizationMappingService.consent(authorizationRequest, user.userId, user.sessionId, approve)
        return "redirect:" + AuthorizeRedirectUrlBuilder.byDecision(decision, authorizationRequest)
    }

    /** 클라이언트·redirect_uri·scope·PKCE 검증 실패 — 리다이렉트하지 않고 오류 화면. `invalid_client` 401, 그 외 400. */
    @ExceptionHandler(OAuth2Exception::class)
    fun handleOAuth2Exception(e: OAuth2Exception): ModelAndView {
        val status = if (e.error == OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT) HttpStatus.UNAUTHORIZED else HttpStatus.BAD_REQUEST
        return ModelAndView(VIEW_ERROR, mapOf("error" to e.error, "errorDescription" to e.errorDescription), status)
    }

    private fun loginFailed(model: Model, message: String?): String {
        model.addAttribute("loginError", message ?: "로그인에 실패했습니다")
        return VIEW_LOGIN
    }

    private fun consentRequest(
        clientId: String,
        redirectUri: String,
        scope: String?,
        state: String?,
        codeChallenge: String?,
        codeChallengeMethod: String?,
        nonce: String?,
    ) = AuthorizationRequestDto(
        responseType = "code",
        clientId = clientId,
        redirectUri = redirectUri,
        scope = scope,
        state = state,
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
        nonce = nonce,
    )

    companion object {
        private const val VIEW_LOGIN = "oauth2/login"
        private const val VIEW_CONSENT = "oauth2/consent"
        private const val VIEW_ERROR = "oauth2/error"
        private const val DEVICE_TYPE = "WEB_AUTHORIZE"
    }
}