package com.rex.api.pub.skeleton.oidc

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.support.BrowserSession
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.authorizationCodeViaBrowser
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.createOAuth2TestClient
import com.rex.api.pub.skeleton.support.exchangeAuthorizationCode
import com.rex.api.pub.skeleton.support.getJson
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postForm
import com.rex.api.pub.skeleton.support.putWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64

/**
 * discovery 가 광고하는 값마다 실제로 동작하는지 대조한다 — 광고했는데 안 되는 것도, 되는데 광고하지 않은 것도 결함이다.
 *
 * 예전 discovery 는 `token`/`id_token` response type, HS256, `client_secret_basic`(미구현), `phone` 을 광고했고
 * `grant_types_supported`·`code_challenge_methods_supported` 는 아예 없었다. userinfo 는 이 서버가 발급한 토큰으로 401 이었다.
 *
 * ```bash
 * ./gradlew e2eTest --tests "*.oidc.DiscoveryConformanceE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class DiscoveryConformanceE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] DiscoveryConformanceE2ETest ${janitor.cleanup(resources)}") }

    val json = JsonMapper.builder().build()
    fun newBrowser() = BrowserSession(restTemplate.rootUri.orEmpty())
    fun enc(value: String) = BrowserSession.encode(value)

    @Suppress("UNCHECKED_CAST")
    fun discovery(): Map<String, Any> = restTemplate.getJson<Map<String, Any>>("/.well-known/openid-configuration").body.shouldNotBeNull()

    @Suppress("UNCHECKED_CAST")
    fun advertisedValues(key: String): List<String> = discovery()[key].shouldNotBeNull() as List<String>

    @Suppress("UNCHECKED_CAST")
    fun jwtPart(jwt: String, index: Int): Map<String, Any> =
        json.readValue(String(Base64.getUrlDecoder().decode(jwt.split(".")[index]), Charsets.UTF_8), Map::class.java) as Map<String, Any>

    fun unauthenticatedAuthorize(clientId: String, responseType: String, scope: String): String {
        val browser = newBrowser()
        val query = "response_type=${enc(responseType)}&client_id=${enc(clientId)}&redirect_uri=${enc("http://localhost:8080/callback")}&scope=${enc(scope)}&state=d"
        return browser.location(browser.get("/oauth2/authorize?$query"))
    }

    fun tokenWithBasic(clientId: String, clientSecret: String): Int =
        newBrowser().postForm("/oauth2/token", mapOf("grant_type" to "client_credentials"), basic = clientId to clientSecret).statusCode()

    describe("response_types_supported · scopes_supported") {
        it("광고한 response type·scope 는 검증을 통과하고(로그인 화면으로), 광고하지 않은 token 은 unsupported_response_type") {
            val (clientId, _) = restTemplate.createOAuth2TestClient(resources)
            val allScopes = advertisedValues("scopes_supported").joinToString(" ")

            advertisedValues("response_types_supported").forEach { responseType ->
                unauthenticatedAuthorize(clientId, responseType, allScopes) shouldBe "/oauth2/login"
            }
            unauthenticatedAuthorize(clientId, "token", "openid") shouldStartWith "http://localhost:8080/callback?error=unsupported_response_type"
        }
    }

    describe("grant_types_supported · token_endpoint_auth_methods_supported · code_challenge_methods_supported") {
        it("광고한 grant·인증 방식·PKCE 방식이 각각 성공한다") {
            val user = restTemplate.createAndLoginUser(resources, "e2e_disc_")
            val (clientId, clientSecret) = restTemplate.createOAuth2TestClient(resources)
            val grants = advertisedValues("grant_types_supported")
            val authMethods = advertisedValues("token_endpoint_auth_methods_supported")

            // authorization_code (+ PKCE 방식마다)
            // 광고값이 이 테스트가 실제로 돌려 보는 grant 와 정확히 같아야 한다 — 광고에 새 grant 를 넣으면 그 왕복을 여기 추가해야 통과한다
            grants.toSet() shouldBe setOf("authorization_code", "refresh_token", "client_credentials")
            var refreshToken: String? = null
            advertisedValues("code_challenge_methods_supported").forEach { method ->
                val verifier = "verifier-${method.lowercase()}-0123456789-abcdefghijklmnopqrstuvwxyz"
                val challenge =
                    when (method) {
                        "S256" -> Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
                        else -> verifier
                    }
                val code = restTemplate.authorizationCodeViaBrowser(
                    clientId,
                    user.loginId,
                    "openid offline_access",
                    "&code_challenge=${enc(challenge)}&code_challenge_method=$method",
                )
                val tokens = restTemplate.exchangeAuthorizationCode(clientId, clientSecret, code, codeVerifier = verifier)
                refreshToken = tokens["refresh_token"] as String?
            }

            // refresh_token
            restTemplate.postForm<Map<String, Any>>(
                "/oauth2/token",
                LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "refresh_token")
                    add("refresh_token", refreshToken.shouldNotBeNull())
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                },
            ).statusCode shouldBe HttpStatus.OK

            // client_credentials × 광고한 인증 방식마다
            authMethods.forEach { method ->
                val status =
                    when (method) {
                        "client_secret_basic" -> tokenWithBasic(clientId, clientSecret)
                        "client_secret_post" ->
                            restTemplate.postForm<Map<String, Any>>(
                                "/oauth2/token",
                                LinkedMultiValueMap<String, String>().apply {
                                    add("grant_type", "client_credentials")
                                    add("client_id", clientId)
                                    add("client_secret", clientSecret)
                                },
                            ).statusCode.value()
                        else -> error("광고했지만 이 테스트가 모르는 인증 방식: $method")
                    }
                status shouldBe 200
            }
        }
    }

    describe("id_token_signing_alg_values_supported · userinfo_endpoint · claims_supported") {
        it("id_token alg 가 광고값이고, userinfo 가 이 서버 토큰으로 200 이고, 클레임 광고와 실제 출력이 일치한다") {
            val user = restTemplate.createAndLoginUser(resources, "e2e_disc_")
            restTemplate.putWithAuth<String>(
                "/user/profile",
                user.accessToken,
                UserProfileUpdateDto(name = "디스커버리", nickname = "disc", phone = null, profileImage = "https://cdn.example.test/p.png"),
            ).statusCode shouldBe HttpStatus.OK
            val (clientId, clientSecret) = restTemplate.createOAuth2TestClient(resources)
            val code = restTemplate.authorizationCodeViaBrowser(clientId, user.loginId, "openid profile email", "&nonce=n-disc")
            val tokens = restTemplate.exchangeAuthorizationCode(clientId, clientSecret, code)
            val idToken = tokens["id_token"].shouldNotBeNull() as String

            jwtPart(idToken, 0)["alg"] shouldBe advertisedValues("id_token_signing_alg_values_supported").single()

            val userinfoEndpoint = discovery()["userinfo_endpoint"] as String
            userinfoEndpoint.substringAfter("/oauth2/") shouldBe "userinfo"
            val userinfo = restTemplate.getWithAuth<Map<String, Any>>("/oauth2/userinfo", tokens["access_token"] as String)
            userinfo.statusCode shouldBe HttpStatus.OK

            val emitted = jwtPart(idToken, 1).keys + userinfo.body.shouldNotBeNull().keys
            val advertised = advertisedValues("claims_supported").toSet()
            (emitted - advertised).shouldBeEmpty() // 내보내는데 광고하지 않은 클레임
            (advertised - emitted).shouldBeEmpty() // 광고했는데 내보내지 않는 클레임
        }
    }
})