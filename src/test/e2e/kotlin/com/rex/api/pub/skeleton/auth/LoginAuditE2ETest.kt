package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.signupUser
import com.rex.api.pub.skeleton.support.superAdminToken
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus

/**
 * 비밀번호 로그인이 감사 로그를 남긴다 — 예전에는 `logLoginSuccess`·`logLoginFailure` 를 아무도 부르지 않아 소셜 로그인만 기록됐다.
 *
 * ```bash
 * ./gradlew e2eTest --tests "*.auth.LoginAuditE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class LoginAuditE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] LoginAuditE2ETest ${janitor.cleanup(resources)}") }

    @Suppress("UNCHECKED_CAST")
    fun auditRows(eventType: String, username: String): List<Map<String, Any?>> {
        val page = restTemplate.getWithAuth<Map<String, Any>>("/admin/audit/logs/search?eventType=$eventType&size=200", restTemplate.superAdminToken())
        page.statusCode shouldBe HttpStatus.OK
        return (page.body.shouldNotBeNull()["content"] as List<Map<String, Any?>>).filter { it["username"] == username }
    }

    describe("비밀번호 로그인 감사") {
        it("틀린 비밀번호는 LOGIN_FAILURE(사용자 ID 없음), 성공은 LOGIN_SUCCESS(사용자 ID 포함)를 남긴다") {
            val password = "Password123!"
            val (loginId, userId) = restTemplate.signupUser(resources, "e2e_laudit_", password = password)

            restTemplate.postJson<Map<String, Any>>("/auth/login", LoginRequestDto(loginId = loginId, password = "wrong-password")).statusCode shouldBe
                HttpStatus.UNAUTHORIZED
            val failures = auditRows("LOGIN_FAILURE", loginId)
            failures shouldHaveSize 1
            failures.single()["userId"] shouldBe null
            failures.single()["result"] shouldBe "FAILURE"
            failures.single()["failureReason"] shouldBe "INVALID_CREDENTIALS"

            restTemplate.postJson<Map<String, Any>>("/auth/login", LoginRequestDto(loginId = loginId, password = password)).statusCode shouldBe HttpStatus.OK
            val successes = auditRows("LOGIN_SUCCESS", loginId)
            successes shouldHaveSize 1
            (successes.single()["userId"] as Number).toLong() shouldBe userId
        }
    }
})