package com.rex.api.pub.skeleton.oidc

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.support.BrowserSession
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createOAuth2TestClient
import com.rex.api.pub.skeleton.support.exchangeAuthorizationCode
import com.rex.api.pub.skeleton.support.signupUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.Base64

/**
 * id_token `auth_time` 은 사용자가 **로그인한 시각**이다(OIDC Core §2) — 예전에는 토큰 발급 시각을 넣었다.
 *
 * 같은 브라우저 세션으로 로그인 뒤 시간을 두고 다시 authorize 하면, 직접 발급(1st-party)·동의 후 발급(3rd-party) 모두
 * `auth_time` 은 로그인 시각에 머물고 `iat` 만 뒤로 간다.
 *
 * ```bash
 * ./gradlew e2eTest --tests "*.oidc.AuthTimeE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AuthTimeE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AuthTimeE2ETest ${janitor.cleanup(resources)}") }

    val json = JsonMapper.builder().build()
    val redirectUri = "http://localhost:8080/callback"
    fun enc(value: String) = BrowserSession.encode(value)
    fun authorizePath(clientId: String) =
        "/oauth2/authorize?response_type=code&client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}&scope=${enc("openid")}&state=at"
    fun codeOf(location: String): String {
        location shouldStartWith "$redirectUri?code="
        return location.substringAfter("code=").substringBefore("&")
    }

    @Suppress("UNCHECKED_CAST")
    fun idTokenClaims(tokens: Map<String, Any>): Map<String, Any> {
        val payload = (tokens["id_token"] as String).split(".")[1]
        return json.readValue(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8), Map::class.java) as Map<String, Any>
    }

    /** 새 브라우저로 로그인만 한다 — (브라우저, 로그인 직전·직후 시각 범위). */
    fun loggedInBrowser(firstPartyClientId: String, loginId: String): Triple<BrowserSession, Long, Long> {
        val browser = BrowserSession(restTemplate.rootUri.orEmpty())
        browser.location(browser.get(authorizePath(firstPartyClientId))) shouldBe "/oauth2/login"
        val before = Instant.now().epochSecond
        val login =
            browser.postForm("/oauth2/login", mapOf("loginId" to loginId, "password" to E2ETestDataSetup.DEFAULT_TEST_PASSWORD, "_csrf" to browser.csrf()))
        val after = Instant.now().epochSecond
        browser.location(login) shouldStartWith "/oauth2/authorize?"
        return Triple(browser, before, after)
    }

    describe("auth_time") {
        it("로그인 뒤 시간이 지나 다시 authorize 해도 직접 발급·동의 후 발급 모두 auth_time 은 로그인 시각이다") {
            val (loginId, _) = restTemplate.signupUser(resources, "e2e_authtime_")
            val (firstPartyId, firstPartySecret) = restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid"))
            val (thirdPartyId, thirdPartySecret) = restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid"), isFirstParty = false)
            val (browser, loginFrom, loginTo) = loggedInBrowser(firstPartyId, loginId)

            Thread.sleep(2_100) // iat 와 auth_time 이 초 단위로 갈라지게

            // 직접 발급(1st-party) — 같은 브라우저 세션으로 다시 authorize
            val direct = idTokenClaims(restTemplate.exchangeAuthorizationCode(firstPartyId, firstPartySecret, codeOf(browser.location(browser.get(authorizePath(firstPartyId))))))
            // 동의 후 발급(3rd-party)
            browser.location(browser.get(authorizePath(thirdPartyId))) shouldStartWith "/oauth2/consent?"
            val consented =
                browser.postForm(
                    "/oauth2/consent",
                    mapOf(
                        "client_id" to thirdPartyId,
                        "redirect_uri" to redirectUri,
                        "scope" to "openid",
                        "state" to "at",
                        "approve" to "true",
                        "_csrf" to browser.csrf(),
                    ),
                )
            val afterConsent = idTokenClaims(restTemplate.exchangeAuthorizationCode(thirdPartyId, thirdPartySecret, codeOf(browser.location(consented))))

            listOf(direct, afterConsent).forEach { claims ->
                val authTime = (claims["auth_time"] as Number).toLong()
                val iat = (claims["iat"] as Number).toLong()
                authTime shouldBeGreaterThanOrEqual loginFrom
                authTime shouldBeLessThanOrEqual loginTo
                iat shouldBeGreaterThanOrEqual authTime + 2
            }
        }
    }
})