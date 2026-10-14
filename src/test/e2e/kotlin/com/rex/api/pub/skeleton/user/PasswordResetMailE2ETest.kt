package com.rex.api.pub.skeleton.user

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetConfirmDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetRequestDto
import com.rex.api.pub.skeleton.service.notification.DemoInboxMailSender
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.signupUser
import com.rex.api.pub.skeleton.support.uniqueE2eLoginId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus

/**
 * 비밀번호 재설정 토큰이 **메일로만** 나가고, 데모 수신함([DemoInboxMailSender], `e2e` 프로파일 빈을 직접 주입 — HTTP 표면 없음)으로 받은 토큰이 실제로 동작하는 왕복.
 *
 * R-1 reset-request 200 → 수신함에 메일 1통(수신자·제목·본문에 토큰) → 토큰으로 reset 200 → 새 비밀번호로 로그인 200 · 옛 비밀번호 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class PasswordResetMailE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
    private val demoInbox: DemoInboxMailSender,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] PasswordResetMailE2ETest ${janitor.cleanup(resources)}") }

    describe("비밀번호 재설정 — 메일 수신함 왕복") {
        it("R-1 요청 → 수신함의 토큰 → 재설정 → 새 비밀번호 로그인") {
            val loginId = uniqueE2eLoginId("e2e_mail_")
            val email = "$loginId@example.test"
            val oldPassword = "MailFlow!2026"
            val newPassword = "MailFlow!2027"
            restTemplate.signupUser(resources, "e2e_mail_", oldPassword, email, "메일 검증", loginId)

            val requested = restTemplate.postJson<String>("/user/password/reset-request", PasswordResetRequestDto(email = email))
            requested.statusCode shouldBe HttpStatus.OK

            val mail = demoInbox.latest(email).shouldNotBeNull().message
            mail.to shouldBe email
            mail.subject shouldContain "비밀번호 재설정"
            val token = Regex("token=([A-Za-z0-9_-]+)").find(mail.body).shouldNotBeNull().groupValues[1]

            val reset = restTemplate.postJson<String>("/user/password/reset", PasswordResetConfirmDto(token = token, newPassword = newPassword))
            reset.statusCode shouldBe HttpStatus.OK

            restTemplate.postJson<TokenResponseDto>("/auth/login", LoginRequestDto(loginId = loginId, password = newPassword)).statusCode shouldBe HttpStatus.OK
            restTemplate.postJson<String>("/auth/login", LoginRequestDto(loginId = loginId, password = oldPassword)).statusCode shouldBe HttpStatus.UNAUTHORIZED
        }
    }
})