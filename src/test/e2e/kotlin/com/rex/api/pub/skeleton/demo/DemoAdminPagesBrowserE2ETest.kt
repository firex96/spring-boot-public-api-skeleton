package com.rex.api.pub.skeleton.demo

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.config.PlaywrightE2ETestConfiguration
import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.security.BlockIpRequestDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.collectClientErrors
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.navigateAndWait
import com.rex.api.pub.skeleton.support.postWithAuth
import com.rex.api.pub.skeleton.support.superAdminToken
import io.kotest.assertions.fail
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

/**
 * 데모 관리자 페이지 7 이 실제 API 위에서 렌더되는지 브라우저로 확인한다.
 *
 * `DemoTemplateContractTest` 는 필드명·경로·본문 키를 정적으로 대조하지만 경로 변수의 타입(`/blocked-ips/{id}` vs `/ip/{ipAddress}`)과
 * 실제 렌더 결과는 보지 못한다. 여기서는 시드 관리자 토큰을 `localStorage` 에 넣고 각 페이지를 열어 표가 값으로 채워지고
 * `undefined` 가 없으며 클라이언트 오류(console error·예외·`/admin/` 4xx/5xx)가 0 인지 본다.
 *
 * 전제: e2e 프로파일은 Flyway 를 끄고 스켈레톤 MySQL 을 그대로 쓴다 — 역할·권한 시드(`V2__insert_initial_data.sql`)가
 * 이미 적용돼 있어야 한다(`beforeSpec` 이 API 로 확인하고 없으면 즉시 실패한다). 계정 시드는 `V3__insert_e2e_test_data.sql`.
 *
 * 실행: `./gradlew e2eTest --tests "*.demo.DemoAdminPagesBrowserE2ETest"` (로컬은 브라우저 창이 뜬다 — `CI=true` 면 headless)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(PlaywrightE2ETestConfiguration::class)
@ActiveProfiles("local", "e2e")
@Tags("browser")
class DemoAdminPagesBrowserE2ETest(
    private val browser: Browser,
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
    private val e2eTestDataSetup: E2ETestDataSetup,
    @LocalServerPort private val port: Int,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] DemoAdminPagesBrowserE2ETest ${janitor.cleanup(resources)}") }

    val baseUrl = "http://localhost:$port"
    lateinit var token: String

    beforeSpec {
        token = restTemplate.superAdminToken()
        val groups = restTemplate.getWithAuth<List<Map<String, Any>>>("/admin/groups", token)
        val permissions = restTemplate.getWithAuth<List<Map<String, Any>>>("/admin/permissions", token)
        if (groups.statusCode != HttpStatus.OK || groups.body?.none { it["code"] == "SUPER_ADMIN" } != false ||
            permissions.statusCode != HttpStatus.OK || permissions.body?.none { it["code"] == "oauth2:manage" } != false
        ) {
            fail("V2 초기 데이터 없음 — local 프로파일로 한 번 기동해 Flyway 를 적용하라 (역할 SUPER_ADMIN · 권한 oauth2:manage)")
        }
    }

    /** 토큰을 `localStorage` 에 주입하고(데모 페이지의 `apiCall` 이 읽는 키) 페이지를 연다. 클라이언트 오류 목록을 함께 준다. */
    fun openPage(path: String, block: (Page, MutableList<String>) -> Unit) {
        val context = browser.newContext(Browser.NewContextOptions().setViewportSize(1440, 900).setLocale("ko-KR"))
        try {
            context.addInitScript("localStorage.setItem('accessToken', '$token');")
            val page = context.newPage()
            val errors = page.collectClientErrors()
            page.navigateAndWait("$baseUrl$path")
            block(page, errors)
        } finally {
            context.close()
        }
    }

    describe("데모 관리자 페이지") {

        it("OAuth2 클라이언트 페이지가 이름과 활성 상태를 렌더한다") {
            val clientName = "E2E Demo Page ${System.nanoTime().toString(16)}"
            val created =
                restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    token,
                    CreateOAuth2ClientRequestDto(clientName = clientName, redirectUris = listOf("https://demo.example.com/callback")),
                )
            created.statusCode.is2xxSuccessful shouldBe true
            resources.oauth2Client(created.body.shouldNotBeNull()["clientId"] as String)

            openPage("/demo/admin/oauth2-clients") { page, errors ->
                val row = page.locator("#clientTableBody tr", Page.LocatorOptions()).filter(com.microsoft.playwright.Locator.FilterOptions().setHasText(clientName))
                assertThat(row).hasCount(1)
                assertThat(row).containsText("활성")
                page.locator("#clientTableBody").innerText() shouldNotContain "undefined"
                errors.toList() shouldBe emptyList()
            }
        }

        it("그룹 페이지가 시드 역할의 코드를 렌더한다") {
            openPage("/demo/admin/groups") { page, errors ->
                assertThat(page.locator("#groupTableBody")).containsText("SUPER_ADMIN")
                val body = page.locator("#groupTableBody").innerText()
                body shouldNotContain "undefined"
                body shouldNotContain "불러오기 실패"
                errors.toList() shouldBe emptyList()
            }
        }

        it("권한 페이지가 코드를 렌더하고 리소스 타입 필터가 by-resource 라우트로 동작한다") {
            openPage("/demo/admin/permissions") { page, errors ->
                assertThat(page.locator("#permissionTableBody")).containsText("oauth2:manage")
                page.selectOption("#resourceTypeFilter", "OAUTH2")
                assertThat(page.locator("#permissionTableBody")).containsText("oauth2:manage")
                assertThat(page.locator("#permissionTableBody")).not().containsText("user:read")
                errors.toList() shouldBe emptyList()
            }
        }

        it("보안 페이지가 차단 사유·영구 여부와 보안 설정을 렌더한다") {
            val ip = "203.0.113.7"
            val blocked =
                restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/security/blocked-ips",
                    token,
                    BlockIpRequestDto(ipAddress = ip, reason = BlockReason.MANUAL, description = "e2e demo page", durationMinutes = null),
                )
            blocked.statusCode.is2xxSuccessful shouldBe true
            resources.blockedIp(ip)

            openPage("/demo/admin/security") { page, errors ->
                val row = page.locator("#blockedIpTableBody tr").filter(com.microsoft.playwright.Locator.FilterOptions().setHasText(ip))
                assertThat(row).containsText("MANUAL")
                assertThat(row).containsText("예")
                assertThat(page.locator("#maxLoginAttempts")).not().hasText("-")
                errors.toList() shouldBe emptyList()
            }
        }

        it("감사 로그 페이지가 로그 행을 렌더한다") {
            openPage("/demo/admin/audit-logs") { page, errors ->
                // 오류 행도 tr 이라 개수만 보면 실패를 통과시킨다 — 상세 버튼이 있는 실제 로그 행을 센다
                page.locator("#logTableBody tr button").count() shouldBeGreaterThanOrEqual 1
                val body = page.locator("#logTableBody").innerText()
                body shouldNotContain "undefined"
                body shouldNotContain "오류:"
                body shouldNotContain "불러오기 실패"
                errors.toList() shouldBe emptyList()
            }
        }

        it("대시보드가 카운트 3 을 채우고 사용자 수는 API 가 없어 — 로 둔다") {
            openPage("/demo/admin") { page, errors ->
                assertThat(page.locator("#userCount")).hasText("—")
                page.locator("#groupCount").innerText().toInt() shouldBeGreaterThanOrEqual 3
                page.locator("#recentLogs .log-item").count() shouldBeGreaterThanOrEqual 1
                errors.toList() shouldBe emptyList()
            }
        }

        it("차단 IP 값에 따옴표·태그가 들어 있어도 핸들러를 탈출하지 못한다") {
            // ipAddress 는 @NotBlank 뿐이라 형식 검증이 없다 — 관리자 오타·공격자 헤더로 이런 값이 저장될 수 있다.
            // 예전 템플릿은 이 값을 `onclick="unblockIp('…')"` 안에 넣었고, escapeHtml 로 &#39; 가 돼도 HTML 파서가 먼저 복원해 핸들러가 깨졌다.
            // `') + (window.__xss = 1) + ('` 는 인라인 handler 안에서 유효한 JS 표현식이 된다 — 문법 오류로 조용히 죽지 않고 실제로 실행된다
            val ip = """1.2.3.4') + (window.__xss = 1) + ('"""
            val blocked =
                restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/security/blocked-ips",
                    token,
                    BlockIpRequestDto(ipAddress = ip, reason = BlockReason.MANUAL, description = """probe '"<b>bold</b>""", durationMinutes = null),
                )
            blocked.statusCode.is2xxSuccessful shouldBe true
            resources.blockedIp(ip)

            openPage("/demo/admin/security") { page, errors ->
                page.onDialog { it.accept() } // 해제 버튼의 confirm
                val row = page.locator("#blockedIpTableBody tr").filter(com.microsoft.playwright.Locator.FilterOptions().setHasText("window.__xss"))
                assertThat(row).hasCount(1)
                // description 의 태그는 HTML 문맥이라 escapeHtml 이 막는다 — 노드로 살아나면 안 된다
                page.locator("#blockedIpTableBody b").count() shouldBe 0
                page.evaluate("() => window.__xss ?? null") shouldBe null

                // data-index 로 넘긴 식별자가 원문 그대로 해제 API 에 실려야 한다(위임 리스너가 올바른 행을 고른다)
                row.locator("button[data-action='unblock']").click()
                assertThat(row).hasCount(0)
                page.evaluate("() => window.__xss ?? null") shouldBe null
                errors.toList() shouldBe emptyList()
            }
        }

        it("사용자 페이지가 ID 로 그룹·유효 권한을 조회한다") {
            val userId = e2eTestDataSetup.getTestUserId("SUPER_ADMIN").shouldNotBeNull()
            openPage("/demo/admin/users") { page, errors ->
                page.fill("#targetUserId", userId.toString())
                page.click("#loadUserAccessButton")
                assertThat(page.locator("#currentGroups")).containsText("SUPER_ADMIN")
                page.locator("#currentPermissions").innerText() shouldNotContain "undefined"
                errors.toList() shouldBe emptyList()
            }
        }
    }
})