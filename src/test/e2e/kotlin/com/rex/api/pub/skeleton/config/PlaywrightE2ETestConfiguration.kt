package com.rex.api.pub.skeleton.config

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * Playwright 브라우저 자동화 E2E 테스트용 설정.
 *
 * 소셜 로그인과 같이 실제 브라우저 상호작용이 필요한 테스트에 사용합니다.
 * 고정 포트(8888)를 사용하여 OAuth 리다이렉트 URL을 등록할 수 있습니다.
 *
 * 사용 예시:
 * ```kotlin
 * @SpringBootTest(
 *     webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
 *     properties = ["server.port=8888"]
 * )
 * @Import(PlaywrightE2ETestConfiguration::class)
 * class SocialLoginBrowserE2ETest {
 *     // ...
 * }
 * ```
 *
 * `E2ETestConfiguration` 을 `@Import` 해 시드 가드·정리기·테스트 캐시 매니저를 함께 얻는다 —
 * 두 설정을 같은 테스트에서 따로 import 하면 같은 이름의 빈이 두 번 정의돼 `BeanDefinitionOverrideException` 이 난다.
 */
@TestConfiguration
@EnableConfigurationProperties(E2ETestProperties::class)
@Import(E2ETestConfiguration::class)
class PlaywrightE2ETestConfiguration {
    /**
     * Playwright 인스턴스.
     * 테스트 종료 시 자동으로 리소스가 정리됩니다.
     */
    @Bean(destroyMethod = "close")
    fun playwright(): Playwright = Playwright.create()

    /**
     * Chromium 브라우저 인스턴스.
     * headless 모드로 실행됩니다.
     * CI 환경에서는 headless: true, 로컬 디버깅 시에는 headless: false로 변경 가능합니다.
     */
    @Bean(destroyMethod = "close")
    fun browser(playwright: Playwright): Browser =
        playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(System.getenv("CI") != null) // CI 환경에서는 headless, 로컬에서는 브라우저 표시
                .setSlowMo(100.0), // 디버깅용 딜레이 (ms)
        )
}