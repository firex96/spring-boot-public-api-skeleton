package com.rex.api.pub.skeleton.auth

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.rex.api.pub.skeleton.client.social.SocialOAuth2Client
import com.rex.api.pub.skeleton.config.E2ETestProperties
import com.rex.api.pub.skeleton.config.PlaywrightE2ETestConfiguration
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.support.fillInput
import com.rex.api.pub.skeleton.support.navigateAndWait
import com.rex.api.pub.skeleton.support.saveScreenshot
import com.rex.api.pub.skeleton.support.waitForUrlPattern
import com.rex.api.pub.skeleton.support.withPage
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URLDecoder

/**
 * 소셜 로그인 Provider ID 발견 테스트.
 *
 * 이 테스트는 각 소셜 플랫폼에서 테스트 계정의 provider-id를 발견합니다.
 * 발견된 provider-id는 환경변수(E2E_KAKAO_PROVIDER_ID 등)로 설정해야 합니다.
 *
 * ## 실행 방법
 * ```bash
 * ./gradlew e2eTest --tests "*.SocialProviderIdDiscoveryTest"
 * ```
 *
 * ## 결과 확인
 * 테스트 로그에서 다음과 같은 출력을 찾으세요:
 * ```
 * ========================================
 * [KAKAO] Provider ID 발견: 1234567890
 * 환경변수에 다음을 설정하세요:
 *   export E2E_KAKAO_PROVIDER_ID="1234567890"
 * ========================================
 * ```
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8888"],
)
@Import(PlaywrightE2ETestConfiguration::class)
@ActiveProfiles("local", "e2e")
@Tags("discover-provider-ids", "manual")
class SocialProviderIdDiscoveryTest(
    private val browser: Browser,
    private val testProperties: E2ETestProperties,
    private val socialOAuth2Clients: List<SocialOAuth2Client>,
    @LocalServerPort private val port: Int,
) : DescribeSpec({

    val baseUrl = "http://localhost:$port"

    fun getOAuth2Client(provider: SocialProvider): SocialOAuth2Client =
        socialOAuth2Clients.find { it.provider == provider }
            ?: throw IllegalArgumentException("지원하지 않는 소셜 로그인: $provider")

    describe("Kakao Provider ID 발견") {
        it("카카오 로그인 후 provider-id를 출력한다").config(
            enabled = testProperties.kakao.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1~2. 로그인 시작 — 서버가 state 를 이 브라우저 세션에 묶고 provider 로 302 한다
                page.navigateAndWait("$baseUrl/auth/oauth2/KAKAO/authorize")
                performKakaoLogin(page, testProperties.kakao.email!!, testProperties.kakao.password!!)

                // 3. 콜백 대기
                page.waitForUrlPattern("**/auth/oauth2/kakao/callback**|**/demo/auth/callback**", 60000.0)
                page.saveScreenshot("kakao-discovery-result.png")

                // 4. 콜백 URL에서 code 추출
                val currentUrl = page.url()
                if (currentUrl.contains("code=")) {
                    val code = extractQueryParam(currentUrl, "code")
                    if (code != null) {
                        // 5. code로 사용자 정보 조회
                        val oauth2Client = getOAuth2Client(SocialProvider.KAKAO)
                        val tokenResponse = oauth2Client.getAccessToken(code, extractQueryParam(currentUrl, "state"))
                        val userInfo = oauth2Client.getUserInfo(tokenResponse.accessToken)

                        println(
                            """
                            |
                            |========================================
                            |[KAKAO] Provider ID 발견: ${userInfo.providerId}
                            |
                            |환경변수에 다음을 설정하세요:
                            |  export E2E_KAKAO_PROVIDER_ID="${userInfo.providerId}"
                            |========================================
                            |
                            """.trimMargin(),
                        )
                    }
                } else {
                    println("콜백 URL에서 code를 찾을 수 없습니다: $currentUrl")
                }
            }
        }
    }

    describe("Naver Provider ID 발견") {
        it("네이버 로그인 후 provider-id를 출력한다").config(
            enabled = testProperties.naver.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1~2. 로그인 시작 — 서버가 state 를 이 브라우저 세션에 묶고 provider 로 302 한다
                page.navigateAndWait("$baseUrl/auth/oauth2/NAVER/authorize")
                performNaverLogin(page, testProperties.naver.id!!, testProperties.naver.password!!)

                // 3. 콜백 대기
                page.waitForUrlPattern("**/auth/oauth2/naver/callback**|**/demo/auth/callback**", 60000.0)
                page.saveScreenshot("naver-discovery-result.png")

                // 4. 콜백 URL에서 code 추출
                val currentUrl = page.url()
                if (currentUrl.contains("code=")) {
                    val code = extractQueryParam(currentUrl, "code")
                    if (code != null) {
                        // 5. code로 사용자 정보 조회
                        val oauth2Client = getOAuth2Client(SocialProvider.NAVER)
                        val tokenResponse = oauth2Client.getAccessToken(code, extractQueryParam(currentUrl, "state"))
                        val userInfo = oauth2Client.getUserInfo(tokenResponse.accessToken)

                        println(
                            """
                            |
                            |========================================
                            |[NAVER] Provider ID 발견: ${userInfo.providerId}
                            |
                            |환경변수에 다음을 설정하세요:
                            |  export E2E_NAVER_PROVIDER_ID="${userInfo.providerId}"
                            |========================================
                            |
                            """.trimMargin(),
                        )
                    }
                } else {
                    println("콜백 URL에서 code를 찾을 수 없습니다: $currentUrl")
                }
            }
        }
    }

    describe("Google Provider ID 발견") {
        it("구글 로그인 후 provider-id를 출력한다").config(
            enabled = testProperties.google.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1~2. 로그인 시작 — 서버가 state 를 이 브라우저 세션에 묶고 provider 로 302 한다
                page.navigateAndWait("$baseUrl/auth/oauth2/GOOGLE/authorize")
                performGoogleLogin(page, testProperties.google.email!!, testProperties.google.password!!)

                // 3. 콜백 대기
                page.waitForUrlPattern("**/auth/oauth2/google/callback**|**/demo/auth/callback**", 60000.0)
                page.saveScreenshot("google-discovery-result.png")

                // 4. 콜백 URL에서 code 추출
                val currentUrl = page.url()
                if (currentUrl.contains("code=")) {
                    val code = extractQueryParam(currentUrl, "code")
                    if (code != null) {
                        // 5. code로 사용자 정보 조회
                        val oauth2Client = getOAuth2Client(SocialProvider.GOOGLE)
                        val tokenResponse = oauth2Client.getAccessToken(code, extractQueryParam(currentUrl, "state"))
                        val userInfo = oauth2Client.getUserInfo(tokenResponse.accessToken)

                        println(
                            """
                            |
                            |========================================
                            |[GOOGLE] Provider ID 발견: ${userInfo.providerId}
                            |
                            |환경변수에 다음을 설정하세요:
                            |  export E2E_GOOGLE_PROVIDER_ID="${userInfo.providerId}"
                            |========================================
                            |
                            """.trimMargin(),
                        )
                    }
                } else {
                    println("콜백 URL에서 code를 찾을 수 없습니다: $currentUrl")
                }
            }
        }
    }
})

private fun extractQueryParam(url: String, param: String): String? {
    val queryString = url.substringAfter("?", "")
    return queryString.split("&")
        .map { it.split("=", limit = 2) }
        .find { it.size == 2 && it[0] == param }
        ?.get(1)
        ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
}

private fun performKakaoLogin(page: Page, email: String, password: String) {
    val emailInput = page.locator("input[name='loginId'], input[placeholder*='이메일'], input[placeholder*='아이디']").first()
    emailInput.fill(email)
    val passwordInput = page.locator("input[name='password'], input[type='password']").first()
    passwordInput.fill(password)
    page.waitForTimeout(1000.0)
    val loginButton = page.locator("button[type='submit'], button.btn_g.highlight, button:has-text('로그인')").first()
    loginButton.click()
    page.waitForTimeout(3000.0)
}

private fun performNaverLogin(page: Page, loginId: String, password: String) {
    page.fillInput("#id", loginId)
    page.fillInput("#pw", password)
    page.locator("#log\\.login").click()
    page.waitForLoadState(
        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
        Page.WaitForLoadStateOptions().setTimeout(30000.0),
    )
}

private fun performGoogleLogin(page: Page, email: String, password: String) {
    page.fillInput("input[type='email']", email)
    page.locator("#identifierNext").click()
    page.waitForTimeout(3000.0)
    val passwordSelector = "input[type='password']:not([aria-hidden='true']):not([name='hiddenPassword'])"
    page.waitForSelector(
        passwordSelector,
        Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(15000.0),
    )
    page.locator(passwordSelector).first().fill(password)
    page.locator("#passwordNext").click()
    page.waitForLoadState(
        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
        Page.WaitForLoadStateOptions().setTimeout(30000.0),
    )
}