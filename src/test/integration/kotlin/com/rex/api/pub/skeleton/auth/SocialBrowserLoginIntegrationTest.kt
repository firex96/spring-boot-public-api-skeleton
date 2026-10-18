package com.rex.api.pub.skeleton.auth

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.client.social.GoogleOAuth2Client
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

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
class SocialBrowserLoginIntegrationTest(
    @LocalServerPort private val port: Int,
) : DescribeSpec() {
    @MockkBean
    lateinit var googleClient: GoogleOAuth2Client

    private val http: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

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
    private fun HttpResponse<*>.location(): String = headers().firstValue("Location").orElse("").removePrefix("http://localhost:$port")

    init {
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
    }
}