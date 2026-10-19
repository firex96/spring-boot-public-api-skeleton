package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.MfaCodeRequestDto
import com.rex.api.pub.skeleton.dto.auth.MfaLoginRequestDto
import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.support.BrowserSession
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.createOAuth2TestClient
import com.rex.api.pub.skeleton.support.enableMfa
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import com.rex.api.pub.skeleton.util.TotpCodes
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus

/**
 * 2차 인증(TOTP) — 켠 계정은 어느 로그인 경로로도 2단계 없이 세션을 받지 못한다.
 *
 * API(`POST /auth/login` 202 → `POST /auth/login/mfa`) · 인가 서버 로그인 화면(`/oauth2/login` → `/auth/mfa` → authorize) ·
 * 실패 한도와 계정 잠금 공유 · 등록·관리 API. 소셜 경로는 provider 대역이 필요해 통합 테스트(`SocialBrowserLoginIntegrationTest`)가 본다.
 *
 * ```bash
 * ./gradlew e2eTest --tests "*.auth.MfaE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class MfaE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] MfaE2ETest ${janitor.cleanup(resources)}") }

    val password = E2ETestDataSetup.DEFAULT_TEST_PASSWORD
    val redirectUri = "http://localhost:8080/callback"
    fun enc(value: String) = BrowserSession.encode(value)

    data class MfaUser(val loginId: String, val accessToken: String, val codes: TotpCodes)

    fun mfaUser(): MfaUser {
        val user = restTemplate.createAndLoginUser(resources, "e2e_mfa_")
        return MfaUser(user.loginId, user.accessToken, restTemplate.enableMfa(user.accessToken))
    }

    fun passwordLogin(loginId: String) = restTemplate.postJson<Map<String, Any>>("/auth/login", LoginRequestDto(loginId = loginId, password = password))

    fun challengeOf(loginId: String): String {
        val response = passwordLogin(loginId)
        response.statusCode shouldBe HttpStatus.ACCEPTED
        return response.body.shouldNotBeNull()["mfaToken"] as String
    }

    fun secondStep(token: String, code: String) =
        restTemplate.postJson<Map<String, Any>>("/auth/login/mfa", MfaLoginRequestDto(mfaToken = token, method = MfaMethod.TOTP, code = code))

    /** 이 사용자의 대기 중인 challenge 를 Redis 에서 지운다 — 만료를 흉내 낸다(TTL 5분을 기다리지 않는다). */
    fun expireChallengesOf(loginId: String) {
        redisTemplate.keys("mfa:challenge:*")
            .filter { redisTemplate.opsForHash<String, String>().get(it, "loginId") == loginId }
            .forEach { redisTemplate.delete(it) }
    }

    /** 새 브라우저로 authorize → 로그인까지 — 2단계 화면(`/auth/mfa`)에서 멈춘 브라우저. */
    fun browserAtSecondStep(clientId: String, loginId: String): Pair<BrowserSession, String> {
        val browser = BrowserSession(restTemplate.rootUri.orEmpty())
        val authorize = "/oauth2/authorize?response_type=code&client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}&scope=${enc("openid")}&state=mfa"
        browser.location(browser.get(authorize)) shouldBe "/oauth2/login"
        val login = browser.postForm("/oauth2/login", mapOf("loginId" to loginId, "password" to password, "_csrf" to browser.csrf()))
        browser.location(login) shouldBe "/auth/mfa"
        return browser to authorize
    }

    fun BrowserSession.submitCode(code: String) = postForm("/auth/mfa", mapOf("method" to "totp", "code" to code, "_csrf" to csrf()))

    describe("API 로그인") {
        it("2차 인증을 켠 계정은 202 challenge, 2단계 코드로 200 토큰 — 받은 토큰이 유효하다") {
            val user = mfaUser()

            val tokens = secondStep(challengeOf(user.loginId), user.codes.next())

            tokens.statusCode shouldBe HttpStatus.OK
            restTemplate.getWithAuth<String>("/users/me", tokens.body.shouldNotBeNull()["accessToken"] as String).statusCode shouldBe HttpStatus.OK
        }

        it("같은 challenge 를 두 번 쓰지 못한다 — 두 번째는 401") {
            val user = mfaUser()
            val token = challengeOf(user.loginId)

            secondStep(token, user.codes.next()).statusCode shouldBe HttpStatus.OK
            secondStep(token, user.codes.next()).statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("틀린 코드 4번은 400, 5번째는 계정 잠금 423 — challenge 도 지워져 맞는 코드로도 401") {
            val user = mfaUser()
            val token = challengeOf(user.loginId)

            repeat(4) { secondStep(token, user.codes.wrong()).statusCode shouldBe HttpStatus.BAD_REQUEST }
            secondStep(token, user.codes.wrong()).statusCode shouldBe HttpStatus.LOCKED
            secondStep(token, user.codes.next()).statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("challenge 를 바꿔도 실패는 계정 단위로 쌓인다 — 3번 틀린 뒤 비밀번호로 다시 들어와 2번 더 틀리면 423") {
            val user = mfaUser()

            val first = challengeOf(user.loginId)
            repeat(3) { secondStep(first, user.codes.wrong()).statusCode shouldBe HttpStatus.BAD_REQUEST }
            val second = challengeOf(user.loginId) // 1단계 성공이 누적을 지우지 않는다
            secondStep(second, user.codes.wrong()).statusCode shouldBe HttpStatus.BAD_REQUEST
            secondStep(second, user.codes.wrong()).statusCode shouldBe HttpStatus.LOCKED
        }
    }

    describe("인가 서버 브라우저 로그인") {
        it("로그인 뒤 2단계 화면으로 가고, 2단계 전에는 authorize 가 로그인 화면이다 — 통과하면 기다리던 authorize 로 돌아가 코드를 받는다") {
            val user = mfaUser()
            val (clientId, _) = restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid"))
            val (browser, authorize) = browserAtSecondStep(clientId, user.loginId)

            browser.location(browser.get(authorize)) shouldBe "/oauth2/login"
            browser.get("/auth/mfa").statusCode() shouldBe 200

            val submitted = browser.submitCode(user.codes.next())
            browser.location(submitted) shouldStartWith "/oauth2/authorize?"
            browser.location(browser.get(browser.location(submitted))) shouldStartWith "$redirectUri?code="
        }

        it("틀린 코드는 같은 화면에 메시지 — 로그인되지 않는다") {
            val user = mfaUser()
            val (clientId, _) = restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid"))
            val (browser, authorize) = browserAtSecondStep(clientId, user.loginId)

            val submitted = browser.submitCode(user.codes.wrong())
            submitted.statusCode() shouldBe 200
            submitted.body() shouldContain "코드가 올바르지 않습니다"
            browser.location(browser.get(authorize)) shouldBe "/oauth2/login"
        }

        it("challenge 가 만료된 뒤 맞는 코드를 직접 POST 해도 로그인되지 않는다 — 로그인 화면으로") {
            val user = mfaUser()
            val (clientId, _) = restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid"))
            val (browser, authorize) = browserAtSecondStep(clientId, user.loginId)
            expireChallengesOf(user.loginId)

            browser.location(browser.submitCode(user.codes.next())) shouldBe "/oauth2/login?error"
            browser.location(browser.get(authorize)) shouldBe "/oauth2/login"
        }

        it("2단계를 기다리는 동안 다른 경로에서 계정이 잠기면, 맞는 코드라도 로그인되지 않는다") {
            val user = mfaUser()
            val (clientId, _) = restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid"))
            val (browser, authorize) = browserAtSecondStep(clientId, user.loginId)
            val apiChallenge = challengeOf(user.loginId)
            repeat(4) { secondStep(apiChallenge, user.codes.wrong()) }
            secondStep(apiChallenge, user.codes.wrong()).statusCode shouldBe HttpStatus.LOCKED

            browser.location(browser.submitCode(user.codes.next())) shouldBe "/oauth2/login?error"
            browser.location(browser.get(authorize)) shouldBe "/oauth2/login"
        }
    }

    describe("등록·관리 API") {
        it("켜지 않은 계정은 그대로 200 토큰 — 켜면 다른 세션이 폐기되고 켠 세션은 남는다") {
            val user = restTemplate.createAndLoginUser(resources, "e2e_mfa_")
            passwordLogin(user.loginId).statusCode shouldBe HttpStatus.OK
            val other = passwordLogin(user.loginId).body.shouldNotBeNull()["accessToken"] as String

            restTemplate.enableMfa(user.accessToken)

            restTemplate.getWithAuth<String>("/users/me", other).statusCode shouldBe HttpStatus.UNAUTHORIZED
            restTemplate.getWithAuth<String>("/users/me", user.accessToken).statusCode shouldBe HttpStatus.OK
            restTemplate.getWithAuth<Map<String, Any>>("/user/mfa", user.accessToken).body.shouldNotBeNull()["enabled"] shouldBe true
        }

        it("이미 켰으면 setup 은 409, 끄면(현재 코드로 재인증) 다시 200 토큰으로 로그인한다") {
            val user = mfaUser()

            restTemplate.postWithAuth<Map<String, Any>>("/user/mfa/totp/setup", user.accessToken, emptyMap<String, Any>()).statusCode shouldBe HttpStatus.CONFLICT
            restTemplate.postWithAuth<String>(
                "/user/mfa/disable",
                user.accessToken,
                mapOf("method" to "totp", "code" to user.codes.next()),
            ).statusCode shouldBe HttpStatus.NO_CONTENT
            passwordLogin(user.loginId).statusCode shouldBe HttpStatus.OK
        }

        it("관리 API 의 코드 확인은 5번 틀리면 6번째부터 429 — 로그인 잠금과는 별개다") {
            val user = restTemplate.createAndLoginUser(resources, "e2e_mfa_")
            val setup = restTemplate.postWithAuth<Map<String, Any>>("/user/mfa/totp/setup", user.accessToken, emptyMap<String, Any>())
            val codes = TotpCodes(setup.body.shouldNotBeNull()["secret"] as String)

            repeat(5) {
                restTemplate.postWithAuth<String>("/user/mfa/totp/activate", user.accessToken, MfaCodeRequestDto(codes.wrong())).statusCode shouldBe
                    HttpStatus.BAD_REQUEST
            }
            restTemplate.postWithAuth<String>("/user/mfa/totp/activate", user.accessToken, MfaCodeRequestDto(codes.next())).statusCode shouldBe
                HttpStatus.TOO_MANY_REQUESTS
            passwordLogin(user.loginId).statusCode shouldBe HttpStatus.OK // 로그인 잠금은 걸리지 않았다
        }
    }
})