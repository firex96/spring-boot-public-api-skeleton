package com.rex.api.pub.skeleton.controller.oauth2.support

import com.rex.api.pub.skeleton.config.security.browser.BrowserSecurityPaths
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.model.oauth2.AuthorizeDecisionModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 인가 흐름의 리다이렉트 URL 조립 — authorize(`OAuth2AuthorizationController`)와 로그인·동의(`OAuth2BrowserController`)가 함께 쓴다.
 *
 * `redirect_uri` 는 BizService 가 검증한 뒤에만 여기로 온다(RFC 6749 §4.1.2.1) — 이 객체는 검증하지 않는다.
 */
object AuthorizeRedirectUrlBuilder {
    /** 판정 → 리다이렉트 URL. `LOGIN_REQUIRED` 는 로그인 화면(돌아올 authorize URL 은 호출자가 세션에 저장한다). */
    fun byDecision(decision: AuthorizeDecisionModel, request: AuthorizationRequestDto): String =
        when (decision.nextStep) {
            AuthorizeNextStep.LOGIN_REQUIRED -> BrowserSecurityPaths.OAUTH2_LOGIN
            AuthorizeNextStep.CONSENT_REQUIRED -> consentUrl(request)
            AuthorizeNextStep.CODE_ISSUED ->
                codeRedirectUrl(request.redirectUri, requireNotNull(decision.code) { "CODE_ISSUED 판정에는 code 가 있다" }, request.state)
            AuthorizeNextStep.ERROR ->
                errorRedirectUrl(
                    request.redirectUri,
                    requireNotNull(decision.error) { "ERROR 판정에는 error 가 있다" },
                    decision.errorDescription,
                    request.state,
                )
        }

    /** 로그인 뒤 돌아갈 authorize URL — 서버가 검증된 파라미터로 다시 조립한다(쿼리로 받은 복귀 주소를 쓰지 않는다). */
    fun authorizeUrl(request: AuthorizationRequestDto): String =
        "${BrowserSecurityPaths.OAUTH2_AUTHORIZE}?" + (listOf("response_type=${encode(request.responseType)}") + flowParams(request)).joinToString("&")

    private fun consentUrl(request: AuthorizationRequestDto): String = "${BrowserSecurityPaths.OAUTH2_CONSENT}?" + flowParams(request).joinToString("&")

    private fun codeRedirectUrl(redirectUri: String, code: String, state: String?): String {
        val params = mutableListOf("code=${encode(code)}")
        state?.let { params.add("state=${encode(it)}") }
        return append(redirectUri, params)
    }

    private fun errorRedirectUrl(redirectUri: String, error: String, errorDescription: String?, state: String?): String {
        val params = mutableListOf("error=${encode(error)}")
        errorDescription?.let { params.add("error_description=${encode(it)}") }
        state?.let { params.add("state=${encode(it)}") }
        return append(redirectUri, params)
    }

    private fun flowParams(request: AuthorizationRequestDto): List<String> =
        buildList {
            add("client_id=${encode(request.clientId)}")
            add("redirect_uri=${encode(request.redirectUri)}")
            request.scope?.let { add("scope=${encode(it)}") }
            request.state?.let { add("state=${encode(it)}") }
            request.codeChallenge?.let { add("code_challenge=${encode(it)}") }
            request.codeChallengeMethod?.let { add("code_challenge_method=${encode(it)}") }
            request.nonce?.let { add("nonce=${encode(it)}") }
        }

    private fun append(uri: String, params: List<String>): String {
        val separator = if (uri.contains("?")) "&" else "?"
        return "$uri$separator${params.joinToString("&")}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}