package com.rex.api.pub.skeleton.controller.auth

import com.rex.api.pub.skeleton.config.security.browser.AuthorizationRequestStore
import com.rex.api.pub.skeleton.config.security.browser.BrowserSecurityPaths
import com.rex.api.pub.skeleton.config.security.browser.BrowserSessionAuthenticator
import com.rex.api.pub.skeleton.config.security.browser.MfaPendingTokenStore
import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.enums.StringEnumUtil
import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.exception.auth.AccountLockedException
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.exception.auth.MfaException
import com.rex.api.pub.skeleton.service.mapping.BrowserMfaMappingService
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 브라우저 2단계 화면 — 인가 서버 로그인 화면·소셜 콜백이 2차 인증 대상을 여기로 보낸다(브라우저 체인, CSRF).
 *
 * 세션에는 challenge 토큰만 있고 로그인 상태가 아니다. 통과하면 그때 세션을 발급하고 브라우저 세션에 로그인시킨 뒤,
 * 인가 서버 출처는 기다리던 authorize 로, 소셜 출처는 데모 콜백(토큰 교환 페이지)으로 보낸다.
 */
@Tag(name = "Auth", description = "인증 API")
@Controller
@RequestMapping(BrowserSecurityPaths.AUTH_MFA)
class BrowserMfaController(
    private val browserMfaMappingService: BrowserMfaMappingService,
    private val mfaPendingTokenStore: MfaPendingTokenStore,
    private val browserSessionAuthenticator: BrowserSessionAuthenticator,
    private val authorizationRequestStore: AuthorizationRequestStore,
    private val clientIpResolver: ClientIpResolver,
) {
    @Operation(summary = "2단계 화면", description = "기다리는 challenge 가 없거나 만료됐으면 로그인 화면으로")
    @GetMapping
    fun page(request: HttpServletRequest): String {
        val token = mfaPendingTokenStore.peek(request)
        if (token == null || !browserMfaMappingService.isPending(token)) {
            mfaPendingTokenStore.clear(request)
            return "redirect:${BrowserSecurityPaths.OAUTH2_LOGIN}?expired"
        }
        return VIEW_MFA
    }

    @Operation(summary = "2단계 제출", description = "코드가 맞으면 로그인시키고 이어 갈 곳으로 302. 틀리면 같은 화면, challenge 가 끝났거나 계정이 잠겼으면 로그인 화면")
    @PostMapping
    fun submit(
        @RequestParam("method", defaultValue = "totp") method: String,
        @RequestParam("code") code: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
    ): String {
        val token = mfaPendingTokenStore.peek(request) ?: return "redirect:${BrowserSecurityPaths.OAUTH2_LOGIN}?expired"
        val mfaMethod = runCatching { StringEnumUtil.fromValue<MfaMethod>(method) }.getOrNull() ?: return invalidCode(model)
        val result =
            try {
                browserMfaMappingService.complete(token, mfaMethod, code, clientIpResolver.resolve(request))
            } catch (e: MfaException) {
                if (e.errorCode == ErrorCode.MFA_INVALID_CODE) return invalidCode(model)
                return restartLogin(request) // 만료·소비·출처 불일치·한도 초과
            } catch (e: AccountLockedException) {
                return restartLogin(request)
            } catch (e: ForbiddenException) {
                return restartLogin(request)
            }
        mfaPendingTokenStore.clear(request)
        browserSessionAuthenticator.signIn(request, response, result.principal)
        return "redirect:" +
            when (result.origin) {
                MfaOrigin.SOCIAL_BROWSER -> BrowserSecurityPaths.DEMO_CALLBACK
                else -> authorizationRequestStore.take(request) ?: "${BrowserSecurityPaths.OAUTH2_LOGIN}?success"
            }
    }

    private fun invalidCode(model: Model): String {
        model.addAttribute("mfaError", "코드가 올바르지 않습니다")
        return VIEW_MFA
    }

    private fun restartLogin(request: HttpServletRequest): String {
        mfaPendingTokenStore.clear(request)
        return "redirect:${BrowserSecurityPaths.OAUTH2_LOGIN}?error"
    }

    companion object {
        private const val VIEW_MFA = "auth/mfa"
    }
}