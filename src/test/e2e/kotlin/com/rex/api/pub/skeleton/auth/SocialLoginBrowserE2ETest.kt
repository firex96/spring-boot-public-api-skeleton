package com.rex.api.pub.skeleton.auth

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.rex.api.pub.skeleton.config.E2ETestProperties
import com.rex.api.pub.skeleton.config.PlaywrightE2ETestConfiguration
import com.rex.api.pub.skeleton.support.fillInput
import com.rex.api.pub.skeleton.support.getQueryParams
import com.rex.api.pub.skeleton.support.navigateAndWait
import com.rex.api.pub.skeleton.support.saveScreenshot
import com.rex.api.pub.skeleton.support.unescapeJson
import com.rex.api.pub.skeleton.support.waitForUrlPattern
import com.rex.api.pub.skeleton.support.withPage
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * 소셜 로그인 브라우저 자동화 E2E 테스트.
 *
 * Playwright를 사용하여 실제 브라우저에서 소셜 로그인 전체 흐름을 테스트합니다.
 * - 인증 URL로 이동
 * - 소셜 로그인 페이지에서 로그인
 * - 콜백 처리 및 토큰 발급
 *
 * **주의**: 이 테스트는 실제 소셜 계정 자격 증명이 필요합니다.
 * application-e2e.yml의 e2e-test.social-login 설정 또는 환경 변수로 계정 정보를 설정하세요.
 *
 * 실행 방법:
 * ```bash
 * # 로컬에서 브라우저 표시하며 테스트
 * ./gradlew e2eTest --tests "*.SocialLoginBrowserE2ETest"
 *
 * # 환경변수로 계정 설정 시
 * E2E_KAKAO_EMAIL=xxx E2E_KAKAO_PASSWORD=xxx ./gradlew e2eTest --tests "*.SocialLoginBrowserE2ETest"
 *
 * # CI 환경에서 headless 모드로 테스트
 * CI=true ./gradlew e2eTest --tests "*.SocialLoginBrowserE2ETest"
 * ```
 *
 * **전제조건**:
 * 1. 소셜 로그인 개발자 콘솔에 redirect_uri 등록 필요:
 *    - http://localhost:8888/auth/oauth2/kakao/callback
 *    - http://localhost:8888/auth/oauth2/naver/callback
 *    - http://localhost:8888/auth/oauth2/google/callback
 * 2. 테스트용 소셜 계정 준비
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8888"],
)
@Import(PlaywrightE2ETestConfiguration::class)
@ActiveProfiles("local", "e2e")
@Tags("browser", "social-login")
class SocialLoginBrowserE2ETest(
    private val browser: Browser,
    private val testProperties: E2ETestProperties,
    @LocalServerPort private val port: Int,
) : DescribeSpec({

    val baseUrl = "http://localhost:$port"

    // 테스트 계정 (YAML 설정 또는 환경변수에서 로드)
    val kakao = testProperties.kakao
    val naver = testProperties.naver
    val google = testProperties.google

    describe("Kakao 소셜 로그인 전체 흐름") {

        it("인증 URL 조회 시 카카오 로그인 페이지로 리다이렉트된다").config(
            enabled = kakao.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1. API에서 인증 URL 가져오기
                page.navigateAndWait("$baseUrl/auth/oauth2/kakao/authorize")
                val responseText = page.content()

                // JSON 응답에서 authorizationUrl 추출
                val authUrlMatch = """"authorizationUrl"\s*:\s*"([^"]+)"""".toRegex().find(responseText)
                authUrlMatch.shouldNotBeNull()
                val authUrl = unescapeJson(authUrlMatch.groupValues[1])

                // 2. 카카오 로그인 페이지로 이동
                page.navigateAndWait(authUrl)
                page.url() shouldContain "kauth.kakao.com"

                // 스크린샷 저장 (디버깅용)
                page.saveScreenshot("kakao-login-page.png")
            }
        }

        it("카카오 로그인 후 콜백으로 토큰을 받는다").config(
            enabled = kakao.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1. 인증 URL 가져오기
                page.navigateAndWait("$baseUrl/auth/oauth2/kakao/authorize")
                val responseText = page.content()
                val authUrlMatch = """"authorizationUrl"\s*:\s*"([^"]+)"""".toRegex().find(responseText)
                authUrlMatch.shouldNotBeNull()
                val authUrl = unescapeJson(authUrlMatch.groupValues[1])

                // 2. 카카오 로그인
                page.navigateAndWait(authUrl)
                performKakaoLogin(page, kakao.email!!, kakao.password!!)

                // 디버깅: 로그인 후 상태 스크린샷
                page.saveScreenshot("kakao-after-login.png")
                println("카카오 로그인 후 URL: ${page.url()}")

                // 3. 콜백 또는 동의 페이지 또는 데모 콜백 페이지 대기
                page.waitForUrlPattern("**/auth/oauth2/kakao/callback**|**/oauth/authorize**|**/demo/auth/callback**", 60000.0)

                // 동의 페이지가 나타나면 동의 버튼 클릭
                if (page.url().contains("/oauth/authorize")) {
                    val agreeButton = page.locator("button:has-text('동의하고 계속하기')")
                    if (agreeButton.count() > 0) {
                        agreeButton.click()
                        page.waitForUrlPattern("**/demo/auth/callback**", 30000.0)
                    }
                }

                // 4. 결과 확인 - 데모 콜백 페이지로 리다이렉트되었는지 확인
                val currentUrl = page.url()
                val params = page.getQueryParams()
                // 로그인 성공 시 토큰을 받거나 데모 콜백 페이지로 리다이렉트
                (params.containsKey("access_token") || currentUrl.contains("/demo/auth/callback")) shouldBe true

                page.saveScreenshot("kakao-login-result.png")
            }
        }
    }

    describe("Naver 소셜 로그인 전체 흐름") {

        it("인증 URL 조회 시 네이버 로그인 페이지로 리다이렉트된다").config(
            enabled = naver.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1. API에서 인증 URL 가져오기
                page.navigateAndWait("$baseUrl/auth/oauth2/naver/authorize")
                val responseText = page.content()

                val authUrlMatch = """"authorizationUrl"\s*:\s*"([^"]+)"""".toRegex().find(responseText)
                authUrlMatch.shouldNotBeNull()
                val authUrl = unescapeJson(authUrlMatch.groupValues[1])

                // 2. 네이버 로그인 페이지로 이동
                page.navigateAndWait(authUrl)
                page.url() shouldContain "nid.naver.com"

                page.saveScreenshot("naver-login-page.png")
            }
        }

        it("네이버 로그인 후 콜백으로 토큰을 받는다").config(
            enabled = naver.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1. 인증 URL 가져오기
                page.navigateAndWait("$baseUrl/auth/oauth2/naver/authorize")
                val responseText = page.content()
                val authUrlMatch = """"authorizationUrl"\s*:\s*"([^"]+)"""".toRegex().find(responseText)
                authUrlMatch.shouldNotBeNull()
                val authUrl = unescapeJson(authUrlMatch.groupValues[1])

                // 2. 네이버 로그인
                page.navigateAndWait(authUrl)
                performNaverLogin(page, naver.id!!, naver.password!!)

                // 3. 콜백 또는 데모 콜백 페이지 대기
                page.waitForUrlPattern("**/auth/oauth2/naver/callback**|**/demo/auth/callback**", 60000.0)

                // 4. 결과 확인 - 데모 콜백 페이지로 리다이렉트되었는지 확인
                val currentUrl = page.url()
                val params = page.getQueryParams()
                (params.containsKey("access_token") || currentUrl.contains("/demo/auth/callback")) shouldBe true

                page.saveScreenshot("naver-login-result.png")
            }
        }
    }

    describe("Google 소셜 로그인 전체 흐름") {

        it("인증 URL 조회 시 구글 로그인 페이지로 리다이렉트된다").config(
            enabled = google.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1. API에서 인증 URL 가져오기
                page.navigateAndWait("$baseUrl/auth/oauth2/google/authorize")
                val responseText = page.content()

                val authUrlMatch = """"authorizationUrl"\s*:\s*"([^"]+)"""".toRegex().find(responseText)
                authUrlMatch.shouldNotBeNull()
                val authUrl = unescapeJson(authUrlMatch.groupValues[1])

                // 디버깅: 추출된 URL 출력
                println("=== Google Auth URL ===")
                println("Raw match: ${authUrlMatch.groupValues[1]}")
                println("Unescaped: $authUrl")
                println("========================")

                // 2. 구글 로그인 페이지로 이동
                page.navigateAndWait(authUrl)
                page.url() shouldContain "accounts.google.com"

                page.saveScreenshot("google-login-page.png")
            }
        }

        it("구글 로그인 후 콜백으로 토큰을 받는다").config(
            enabled = google.isConfigured(),
        ) {
            browser.withPage { page ->
                // 1. 인증 URL 가져오기
                page.navigateAndWait("$baseUrl/auth/oauth2/google/authorize")
                val responseText = page.content()
                val authUrlMatch = """"authorizationUrl"\s*:\s*"([^"]+)"""".toRegex().find(responseText)
                authUrlMatch.shouldNotBeNull()
                val authUrl = unescapeJson(authUrlMatch.groupValues[1])

                // 2. 구글 로그인
                page.navigateAndWait(authUrl)
                performGoogleLogin(page, google.email!!, google.password!!)

                // 3. 콜백 또는 데모 콜백 페이지 대기
                page.waitForUrlPattern("**/auth/oauth2/google/callback**|**/demo/auth/callback**", 60000.0)

                // 4. 결과 확인 - 데모 콜백 페이지로 리다이렉트되었는지 확인
                val currentUrl = page.url()
                val params = page.getQueryParams()
                (params.containsKey("access_token") || currentUrl.contains("/demo/auth/callback")) shouldBe true

                page.saveScreenshot("google-login-result.png")
            }
        }
    }

    describe("소셜 로그인 URL 형식 테스트 (계정 불필요)") {

        it("카카오 인증 URL이 올바른 형식이다") {
            browser.withPage { page ->
                page.navigateAndWait("$baseUrl/auth/oauth2/kakao/authorize")
                val responseText = page.content()

                responseText shouldContain "authorizationUrl"
                responseText shouldContain "state"
                responseText shouldContain "kauth.kakao.com"
            }
        }

        it("네이버 인증 URL이 올바른 형식이다") {
            browser.withPage { page ->
                page.navigateAndWait("$baseUrl/auth/oauth2/naver/authorize")
                val responseText = page.content()

                responseText shouldContain "authorizationUrl"
                responseText shouldContain "state"
                responseText shouldContain "nid.naver.com"
            }
        }

        it("구글 인증 URL이 올바른 형식이다") {
            browser.withPage { page ->
                page.navigateAndWait("$baseUrl/auth/oauth2/google/authorize")
                val responseText = page.content()

                responseText shouldContain "authorizationUrl"
                responseText shouldContain "state"
                responseText shouldContain "accounts.google.com"
            }
        }
    }
})

/**
 * 카카오 로그인 페이지에서 로그인 수행.
 */
private fun performKakaoLogin(
    page: Page,
    email: String,
    password: String,
) {
    // 이메일 입력 - placeholder나 name 속성으로 찾기
    val emailInput = page.locator("input[name='loginId'], input[placeholder*='이메일'], input[placeholder*='아이디']").first()
    emailInput.fill(email)

    // 비밀번호 입력
    val passwordInput = page.locator("input[name='password'], input[type='password']").first()
    passwordInput.fill(password)

    // 잠시 대기 후 로그인 버튼 클릭
    page.waitForTimeout(1000.0)

    // 로그인 버튼 클릭 - Promise.all로 클릭과 네비게이션 동시 대기
    val loginButton = page.locator("button[type='submit'], button.btn_g.highlight, button:has-text('로그인')").first()
    loginButton.click()

    // 페이지 전환 대기 (최대 60초)
    page.waitForTimeout(3000.0)
}

/**
 * 네이버 로그인 페이지에서 로그인 수행.
 */
private fun performNaverLogin(
    page: Page,
    loginId: String,
    password: String,
) {
    // 아이디 입력
    page.fillInput("#id", loginId)
    // 비밀번호 입력
    page.fillInput("#pw", password)
    // 로그인 버튼 클릭
    page.locator("#log\\.login").click()

    // 로그인 처리 대기
    page.waitForLoadState(
        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
        Page.WaitForLoadStateOptions().setTimeout(30000.0),
    )
}

/**
 * 구글 로그인 페이지에서 로그인 수행.
 */
private fun performGoogleLogin(
    page: Page,
    email: String,
    password: String,
) {
    // 이메일 입력
    page.fillInput("input[type='email']", email)
    page.locator("#identifierNext").click()

    // 페이지 전환 대기
    page.waitForTimeout(3000.0)
    page.saveScreenshot("google-after-email.png")
    println("구글 이메일 입력 후 URL: ${page.url()}")

    // 비밀번호 입력 필드 대기 (visible 상태)
    // Google의 비밀번호 필드는 동적으로 로드되며, 여러 선택자를 시도
    val passwordSelector = "input[type='password']:not([aria-hidden='true']):not([name='hiddenPassword'])"
    page.waitForSelector(
        passwordSelector,
        Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(15000.0),
    )
    page.locator(passwordSelector).first().fill(password)
    page.locator("#passwordNext").click()

    // 로그인 처리 대기
    page.waitForLoadState(
        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
        Page.WaitForLoadStateOptions().setTimeout(30000.0),
    )
}