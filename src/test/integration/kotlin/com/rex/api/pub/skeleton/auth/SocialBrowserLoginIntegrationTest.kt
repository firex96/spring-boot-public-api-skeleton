package com.rex.api.pub.skeleton.auth

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.client.social.GoogleOAuth2Client
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSocialAccountEntityService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 브라우저 소셜 로그인 흐름 — 실제 `SecurityConfiguration`(브라우저 체인)·Spring Session·Redis(클러스터) 를 지난다.
 *
 * provider 는 대역(`@MockkBean GoogleOAuth2Client`)이다 — 외부 계정 없이 기본 실행(`integrationTest`)에서 돈다.
 * 실제 provider 를 쓰는 `SocialLoginBrowserE2ETest` 는 `browser` 태그라 기본 실행에서 빠진다.
 *
 * ```bash
 * ./gradlew integrationTest --tests "*.auth.SocialBrowserLoginIntegrationTest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
// 로그인 보호(계정 잠금)를 켠다 — testcontainers 프로필은 꺼 두고, 꺼진 상태의 isUserLocked 는 항상 false 다. IP 차단은 꺼 둔 채로 둔다
@TestPropertySource(properties = ["security.login-protection.enable-brute-force-protection=true"])
class SocialBrowserLoginIntegrationTest(
    @LocalServerPort private val port: Int,
    private val userEntityService: UserEntityService,
    private val userSocialAccountEntityService: UserSocialAccountEntityService,
    private val loginAttemptBizService: LoginAttemptBizService,
) : DescribeSpec() {
    @MockkBean
    lateinit var googleClient: GoogleOAuth2Client

    private val http: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    /** 연동된 계정의 로그인 ID — 잠금 검증에 쓴다. */
    private lateinit var linkedLoginId: String

    private fun url(path: String) = URI.create("http://localhost:$port$path")

    private fun get(path: String, cookie: String? = null): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(url(path)).GET().apply { cookie?.let { header("Cookie", it) } }.build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /** 소셜 로그인 시작 — 세션 쿠키(`SKELETON_SESSION=…`)와 provider 로 보낸 state 를 돌려준다. */
    private fun startAuthorization(): Pair<String, String> {
        val response = get("/auth/oauth2/google/authorize")
        response.statusCode() shouldBe 302
        val cookie = sessionCookie(response).shouldNotBeNull()
        val location = response.headers().firstValue("Location").orElseThrow()
        val state = URLDecoder.decode(location.substringAfter("state=").substringBefore("&"), StandardCharsets.UTF_8)
        return cookie to state
    }

    private fun sessionCookie(response: HttpResponse<*>): String? =
        response.headers().allValues("Set-Cookie").firstOrNull { it.startsWith("SKELETON_SESSION=") }?.substringBefore(";")

    private fun callbackWithState(cookie: String?, provider: String, state: String?): HttpResponse<String> =
        get("/auth/oauth2/$provider/callback?code=provider-code" + (state?.let { "&state=$it" } ?: ""), cookie)

    /** 서블릿 컨테이너는 Location 을 절대 URL 로 내보낸다 — 출처를 떼고 경로·쿼리로 비교한다. */
    private fun post(path: String, cookie: String?, headers: Map<String, String> = emptyMap()): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(url(path)).POST(HttpRequest.BodyPublishers.noBody())
                .apply {
                    cookie?.let { header("Cookie", it) }
                    headers.forEach { (k, v) -> header(k, v) }
                }.build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /** 데모 콜백 페이지가 받는 CSRF (헤더 이름, 토큰) — 브라우저 체인이 세션에 저장한 값이다. */
    private fun csrfOf(cookie: String): Pair<String, String> {
        val page = get("/demo/auth/callback", cookie).body()
        val token = Regex("""data-token="([^"]+)"""").find(page).shouldNotBeNull().groupValues[1]
        val header = Regex("""data-header="([^"]+)"""").find(page).shouldNotBeNull().groupValues[1]
        return header to token
    }

    private fun postSessionToken(cookie: String?, csrf: Pair<String, String>?): HttpResponse<String> =
        post("/auth/session/token", cookie, csrf?.let { mapOf(it.first to it.second) } ?: emptyMap())

    /** 콜백 성공 응답 — Location 이 정확히 데모 콜백이고 토큰이 URL 에 없다. */
    private fun assertNoTokenInLocation(response: HttpResponse<*>) {
        response.statusCode() shouldBe 302
        response.location() shouldBe "/demo/auth/callback"
        response.location() shouldNotContain "access_token"
        response.location() shouldNotContain "refresh_token"
    }

    /** 연동된 계정으로 콜백까지 마친 브라우저 세션 쿠키. */
    private fun loginThroughCallback(): String {
        every { googleClient.getUserInfo(any()) } returns
            SocialUserInfo(provider = SocialProvider.GOOGLE, providerId = LINKED_PROVIDER_ID, email = null, name = null, profileImage = null)
        val (cookie, state) = startAuthorization()
        val callback = callbackWithState(cookie, "google", state)
        assertNoTokenInLocation(callback)
        return sessionCookie(callback).shouldNotBeNull()
    }

    private fun HttpResponse<*>.location(): String = headers().firstValue("Location").orElse("").removePrefix("http://localhost:$port")

    init {
        beforeSpec {
            linkedLoginId = "it_social_${UUID.randomUUID().toString().take(8)}"
            val user =
                userEntityService.createUser(
                    loginId = linkedLoginId,
                    passwordHash = "{noop}unused",
                    email = null,
                    name = null,
                    nickname = null,
                    phone = null,
                )
            userSocialAccountEntityService.link(
                userId = user.id,
                provider = SocialProvider.GOOGLE.name,
                providerId = LINKED_PROVIDER_ID,
                email = null,
                name = null,
                profileImage = null,
                accessToken = null,
                refreshToken = null,
                tokenExpiresAt = null,
            )
        }

        beforeEach {
            clearMocks(googleClient)
            every { googleClient.provider } returns SocialProvider.GOOGLE
            every { googleClient.getAuthorizationUrl(any()) } answers { "https://accounts.example.test/auth?client_id=cid&state=${firstArg<String>()}" }
            every { googleClient.getAccessToken(any(), any()) } returns OAuth2TokenResponse(accessToken = "provider-access-token")
            every { googleClient.getUserInfo(any()) } returns
                SocialUserInfo(provider = SocialProvider.GOOGLE, providerId = "unlinked-google-id", email = null, name = null, profileImage = null)
        }

        describe("소셜 로그인 시작") {
            it("세션 쿠키 SKELETON_SESSION 을 만들고 state 를 실은 provider URL 로 302 한다") {
                val (cookie, state) = startAuthorization()

                cookie shouldStartWith "SKELETON_SESSION="
                state.length shouldBe 43
            }
        }

        describe("콜백 state 검증") {
            it("같은 쿠키와 발급된 state 면 state 검증을 통과해 코드 교환으로 간다") {
                val (cookie, state) = startAuthorization()

                callbackWithState(cookie, "google", state)

                verify(exactly = 1) { googleClient.getAccessToken("provider-code", state) }
            }

            it("쿠키 없이 같은 state 로 오면 invalid_state 다") {
                val (_, state) = startAuthorization()

                callbackWithState(null, "google", state).location() shouldStartWith "/demo/auth/callback?error=invalid_state"
                verify(exactly = 0) { googleClient.getAccessToken(any(), any()) }
            }

            it("같은 state 의 두 번째 콜백은 invalid_state 다") {
                val (cookie, state) = startAuthorization()
                callbackWithState(cookie, "google", state)

                callbackWithState(cookie, "google", state).location() shouldStartWith "/demo/auth/callback?error=invalid_state"
                verify(exactly = 1) { googleClient.getAccessToken(any(), any()) }
            }

            it("다른 provider 콜백에 그 state 를 쓰면 invalid_state 다") {
                val (cookie, state) = startAuthorization()

                callbackWithState(cookie, "naver", state).location() shouldStartWith "/demo/auth/callback?error=invalid_state"
            }

            it("state 없이 provider 오류만 오면 invalid_state 다") {
                val (cookie, _) = startAuthorization()

                get("/auth/oauth2/google/callback?error=access_denied", cookie).location() shouldStartWith "/demo/auth/callback?error=invalid_state"
            }

            it("같은 쿠키·state 로 콜백 8개를 동시에 보내면 state 통과는 exactly one") {
                val (cookie, state) = startAuthorization()

                val responses =
                    withContext(Dispatchers.IO) {
                        (1..8).map { async { callbackWithState(cookie, "google", state) } }.awaitAll()
                    }

                verify(exactly = 1) { googleClient.getAccessToken(any(), any()) }
                responses.count { it.location().startsWith("/demo/auth/callback?error=invalid_state") } shouldBe 7
            }
        }

        describe("콜백 토큰 교환") {
            it("성공 콜백은 쿼리 없이 데모 콜백으로 302 하고 세션 ID 를 바꾼다") {
                val (cookie, state) = startAuthorization()
                every { googleClient.getUserInfo(any()) } returns
                    SocialUserInfo(provider = SocialProvider.GOOGLE, providerId = LINKED_PROVIDER_ID, email = null, name = null, profileImage = null)

                val callback = callbackWithState(cookie, "google", state)

                assertNoTokenInLocation(callback)
                sessionCookie(callback).shouldNotBeNull() shouldNotBe cookie
            }

            it("교환은 첫 호출만 200 이고 받은 토큰이 유효하다 — 두 번째는 401") {
                val cookie = loginThroughCallback()
                val csrf = csrfOf(cookie)

                val first = postSessionToken(cookie, csrf)
                first.statusCode() shouldBe 200
                val accessToken = Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(first.body()).shouldNotBeNull().groupValues[1]
                http.send(
                    HttpRequest.newBuilder(url("/users/me")).header("Authorization", "Bearer $accessToken").GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                ).statusCode() shouldBe 200

                val second = postSessionToken(cookie, csrf)
                second.statusCode() shouldBe 401
                second.body() shouldContain "UNAUTHORIZED_ERROR"
            }

            it("CSRF 헤더가 없으면 403 ErrorResponseModel 이다") {
                val cookie = loginThroughCallback()

                val response = postSessionToken(cookie, null)

                response.statusCode() shouldBe 403
                response.body() shouldContain "FORBIDDEN_ERROR"
            }

            it("로그인하지 않은 세션은 CSRF 가 맞아도 401 ErrorResponseModel 이다") {
                val (anonymousCookie, _) = startAuthorization()

                val response = postSessionToken(anonymousCookie, csrfOf(anonymousCookie))

                response.statusCode() shouldBe 401
                response.body() shouldContain "UNAUTHORIZED_ERROR"
            }

            it("같은 쿠키·CSRF 로 교환 8개를 동시에 보내면 200 은 exactly one") {
                val cookie = loginThroughCallback()
                val csrf = csrfOf(cookie)

                val responses =
                    withContext(Dispatchers.IO) {
                        (1..8).map { async { postSessionToken(cookie, csrf) } }.awaitAll()
                    }

                responses.count { it.statusCode() == 200 } shouldBe 1
            }
        }

        describe("로그인 보호 — 잠긴 계정은 소셜로도 로그인하지 못한다") {
            it("비밀번호 실패로 잠긴 계정의 콜백·네이티브 코드 교환은 423 이고, 잠금을 풀면 다시 된다") {
                every { googleClient.getUserInfo(any()) } returns
                    SocialUserInfo(provider = SocialProvider.GOOGLE, providerId = LINKED_PROVIDER_ID, email = null, name = null, profileImage = null)
                repeat(5) { loginAttemptBizService.recordFailedAttempt(linkedLoginId, null) }
                loginAttemptBizService.isUserLocked(linkedLoginId) shouldBe true
                try {
                    val (cookie, state) = startAuthorization()
                    callbackWithState(cookie, "google", state).statusCode() shouldBe 423

                    nativeLogin().statusCode() shouldBe 423
                } finally {
                    loginAttemptBizService.unlockUserAccount(linkedLoginId)
                }
                nativeLogin().statusCode() shouldBe 200
            }
        }
    }

    private fun nativeLogin(): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(url("/auth/oauth2/google/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"code":"provider-code"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    companion object {
        private const val LINKED_PROVIDER_ID = "linked-google-id"
    }
}