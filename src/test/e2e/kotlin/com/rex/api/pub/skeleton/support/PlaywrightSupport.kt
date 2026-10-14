package com.rex.api.pub.skeleton.support

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState

/**
 * Playwright 브라우저 테스트를 위한 유틸리티 확장 함수.
 */

/**
 * 새 브라우저 컨텍스트와 페이지를 생성하여 테스트를 실행합니다.
 * 테스트 종료 후 자동으로 리소스가 정리됩니다.
 */
inline fun Browser.withPage(
    viewportWidth: Int = 1280,
    viewportHeight: Int = 720,
    block: (Page) -> Unit,
) {
    val context: BrowserContext =
        newContext(
            Browser.NewContextOptions()
                .setViewportSize(viewportWidth, viewportHeight)
                .setLocale("ko-KR")
                .setTimezoneId("Asia/Seoul"),
        )
    try {
        val page = context.newPage()
        block(page)
    } finally {
        context.close()
    }
}

/**
 * 페이지에서 나오는 클라이언트 오류를 모은다 — console `error`, 처리되지 않은 예외, 그리고 `/admin/` 요청의 4xx/5xx 응답.
 *
 * `apiCall`(static/js/admin.js)은 HTTP 오류를 `console.error` 로 찍지 않고 상태만 돌려주므로, 응답 이벤트까지 봐야
 * 데모 페이지가 실패 응답을 숨기고 있는지 알 수 있다. 반환된 목록은 페이지가 사는 동안 계속 채워진다.
 */
fun Page.collectClientErrors(): MutableList<String> {
    val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
    onConsoleMessage { message -> if (message.type() == "error") errors += "console: ${message.text()}" }
    onPageError { error -> errors += "pageerror: $error" }
    onResponse { response ->
        if (response.url().contains("/admin/") && response.status() >= 400) {
            errors += "http ${response.status()} ${response.request().method()} ${response.url()}"
        }
    }
    return errors
}

/**
 * URL로 이동하고 페이지 로드 완료를 기다립니다.
 */
fun Page.navigateAndWait(
    url: String,
    timeout: Double = 30000.0,
) {
    navigate(url)
    waitForLoadState(
        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
        Page.WaitForLoadStateOptions().setTimeout(timeout),
    )
}

/**
 * 입력 필드에 텍스트를 채웁니다.
 */
fun Page.fillInput(
    selector: String,
    value: String,
) {
    locator(selector).fill(value)
}

/**
 * 버튼을 클릭하고 네비게이션을 기다립니다.
 */
fun Page.clickAndWaitForNavigation(
    selector: String,
    timeout: Double = 30000.0,
) {
    val button = locator(selector)
    button.click()
    waitForLoadState(
        com.microsoft.playwright.options.LoadState.NETWORKIDLE,
        Page.WaitForLoadStateOptions().setTimeout(timeout),
    )
}

/**
 * 특정 URL 패턴을 기다립니다.
 * glob 패턴을 사용합니다.
 */
fun Page.waitForUrlPattern(
    pattern: String,
    timeout: Double = 30000.0,
) {
    // Playwright의 waitForURL은 glob 패턴 또는 정규식 지원
    waitForURL(
        java.util.regex.Pattern.compile(globToRegex(pattern)),
        Page.WaitForURLOptions().setTimeout(timeout),
    )
}

/**
 * Glob 패턴을 정규식으로 변환합니다.
 */
private fun globToRegex(glob: String): String =
    glob
        .replace(".", "\\.")
        .replace("**", ".*")
        .replace("*", "[^/]*")
        .replace("?", ".")

/**
 * 페이지에서 쿠키를 가져옵니다.
 */
fun BrowserContext.getCookies(): List<com.microsoft.playwright.options.Cookie> = cookies()

/**
 * 스크린샷을 저장합니다 (디버깅용).
 */
fun Page.saveScreenshot(
    path: String = "screenshot-${System.currentTimeMillis()}.png",
) {
    screenshot(Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(path)))
}

/**
 * 소셜 로그인 결과를 나타내는 sealed class.
 */
sealed class SocialLoginResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val userId: Long? = null,
    ) : SocialLoginResult()

    data class AccountLinked(
        val message: String,
    ) : SocialLoginResult()

    data class Error(
        val error: String,
        val description: String? = null,
    ) : SocialLoginResult()

    data class Redirect(
        val url: String,
    ) : SocialLoginResult()
}

/**
 * JSON 문자열에서 이스케이프 문자를 언이스케이프합니다.
 * - \/ -> /
 * - \u0026 -> &
 * - HTML 엔티티 (&amp; -> &)
 * - 기타 유니코드 이스케이프 처리
 */
fun unescapeJson(json: String): String =
    json
        .replace("\\/", "/")
        .replace("\\u0026", "&")
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\u003d", "=")
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { matchResult ->
            matchResult.groupValues[1].toInt(16).toChar().toString()
        }
        // HTML 엔티티 처리 (브라우저가 JSON을 HTML로 렌더링할 때)
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

/**
 * 현재 URL에서 쿼리 파라미터를 추출합니다.
 */
fun Page.getQueryParams(): Map<String, String> {
    val url = url()
    val queryString = url.substringAfter("?", "")
    if (queryString.isEmpty()) return emptyMap()

    return queryString.split("&")
        .associate { param ->
            val (key, value) = param.split("=", limit = 2)
            java.net.URLDecoder.decode(key, "UTF-8") to
                java.net.URLDecoder.decode(value, "UTF-8")
        }
}