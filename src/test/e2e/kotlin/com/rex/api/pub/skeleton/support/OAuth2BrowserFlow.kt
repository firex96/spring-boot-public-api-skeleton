package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap
import java.util.UUID

/*
 * 인가 서버의 브라우저 흐름(세션 쿠키 + CSRF 폼)을 HTTP 로 흉내 내 인가 서버 토큰을 받는다 — userinfo·discovery·email_verified e2e 공용.
 * 로그인·동의 왕복 자체의 계약은 OAuth2AuthorizationE2ETest 가 본다. 여기는 "토큰을 얻는 길" 만 제공한다.
 */

/** 관리자 API 로 OAuth2 클라이언트를 만든다 — (clientId, clientSecret). 정리 대상에 등록한다. */
fun TestRestTemplate.createOAuth2TestClient(
    resources: E2eResources,
    scopes: List<String> = listOf("openid", "profile", "email", "offline_access"),
    isFirstParty: Boolean = true,
    requirePkce: Boolean = false,
    clientName: String = "E2E OAuth2 Client ${UUID.randomUUID().toString().take(8)}",
): Pair<String, String> {
    val response =
        postWithAuth<Map<String, Any>>(
            "/admin/oauth2/clients",
            superAdminToken(),
            CreateOAuth2ClientRequestDto(
                clientName = clientName,
                description = "E2E 인가 서버 토큰용",
                grantTypes = listOf("authorization_code", "refresh_token", "client_credentials"),
                redirectUris = listOf(REDIRECT_URI),
                scopes = scopes,
                accessTokenValidity = 3600,
                refreshTokenValidity = 604800,
                isFirstParty = isFirstParty,
                requirePkce = requirePkce,
            ),
        )
    response.statusCode shouldBe HttpStatus.CREATED
    val body = response.body.shouldNotBeNull()
    return resources.oauth2Client(body["clientId"] as String) to body["clientSecret"] as String
}

/**
 * 새 브라우저 세션으로 authorize → 로그인 → authorize 를 지나 first-party 클라이언트의 인가 코드를 받는다.
 * [extraQuery] 는 authorize 쿼리에 그대로 붙인다(예: `&code_challenge=…&code_challenge_method=S256`).
 */
fun TestRestTemplate.authorizationCodeViaBrowser(clientId: String, loginId: String, scope: String, extraQuery: String = ""): String {
    val browser = BrowserSession(rootUri.orEmpty())
    val enc = BrowserSession::encode
    val authorize = "/oauth2/authorize?response_type=code&client_id=${enc(clientId)}&redirect_uri=${enc(REDIRECT_URI)}&scope=${enc(scope)}&state=e2e$extraQuery"
    browser.location(browser.get(authorize)) shouldBe "/oauth2/login"
    val login =
        browser.postForm(
            "/oauth2/login",
            mapOf("loginId" to loginId, "password" to E2ETestDataSetup.DEFAULT_TEST_PASSWORD, "_csrf" to browser.csrf()),
        )
    val codeLocation = browser.location(browser.get(browser.location(login)))
    codeLocation shouldStartWith "$REDIRECT_URI?code="
    return codeLocation.substringAfter("code=").substringBefore("&")
}

/** 인가 코드를 토큰으로 바꾼다(`client_secret_post`). 응답 본문 그대로. */
fun TestRestTemplate.exchangeAuthorizationCode(clientId: String, clientSecret: String, code: String, codeVerifier: String? = null): Map<String, Any> {
    val response =
        postForm<Map<String, Any>>(
            "/oauth2/token",
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", code)
                add("redirect_uri", REDIRECT_URI)
                add("client_id", clientId)
                add("client_secret", clientSecret)
                codeVerifier?.let { add("code_verifier", it) }
            },
        )
    response.statusCode shouldBe HttpStatus.OK
    return response.body.shouldNotBeNull()
}

/** first-party 클라이언트로 브라우저 로그인 → 코드 → 토큰. 인가 서버 access 토큰 응답 본문. */
fun TestRestTemplate.authorizationServerTokens(resources: E2eResources, loginId: String, scope: String): Map<String, Any> {
    val (clientId, clientSecret) = createOAuth2TestClient(resources)
    return exchangeAuthorizationCode(clientId, clientSecret, authorizationCodeViaBrowser(clientId, loginId, scope))
}

private const val REDIRECT_URI = "http://localhost:8080/callback"