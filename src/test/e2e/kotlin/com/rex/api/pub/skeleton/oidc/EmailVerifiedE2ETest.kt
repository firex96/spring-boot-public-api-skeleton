package com.rex.api.pub.skeleton.oidc

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.user.EmailVerificationConfirmDto
import com.rex.api.pub.skeleton.dto.user.EmailVerificationRequestDto
import com.rex.api.pub.skeleton.service.notification.DemoInboxMailSender
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.E2eUser
import com.rex.api.pub.skeleton.support.authorizationServerTokens
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.util.Base64

/**
 * `email_verified` 는 세 응답 경로에서 같은 값이고 실제 인증 여부다 — userinfo · id_token · `GET /user/profile`.
 *
 * 예전에는 userinfo·프로필이 "이메일이 있으면 true", id_token 은 항상 true 였다. 인증 메일 재요청(이전 토큰 무효화)도 인증으로 읽히면 안 된다.
 * 이메일이 인증 기록과 다른 경우는 `EmailVerifiedQueryIntegrationTest` 가 본다(API 로는 인증 없이 이메일을 바꿀 수 없다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class EmailVerifiedE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
    private val demoInbox: DemoInboxMailSender,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] EmailVerifiedE2ETest ${janitor.cleanup(resources)}") }

    fun emailOf(user: E2eUser) = "${user.loginId}@example.com"

    fun profileEmailVerified(token: String): Boolean =
        restTemplate.getWithAuth<Map<String, Any>>("/user/profile", token).body.shouldNotBeNull()["emailVerified"] as Boolean

    fun requestVerificationTwice(user: E2eUser) {
        repeat(2) {
            restTemplate.postWithAuth<String>("/user/email/verify-request", user.accessToken, EmailVerificationRequestDto(email = emailOf(user)))
                .statusCode shouldBe HttpStatus.OK
        }
    }

    /** (userinfo, id_token) 의 email_verified — 인가 서버 토큰(scope openid email)으로. */
    fun oidcEmailVerified(user: E2eUser): Pair<Any?, Any?> {
        val tokens = restTemplate.authorizationServerTokens(resources, user.loginId, "openid email")
        val userinfo = restTemplate.getWithAuth<Map<String, Any>>("/oauth2/userinfo", tokens["access_token"] as String).body.shouldNotBeNull()
        val idTokenPayload = String(Base64.getUrlDecoder().decode((tokens["id_token"] as String).split(".")[1]), Charsets.UTF_8)
        val idTokenValue = Regex(""""email_verified"\s*:\s*(true|false)""").find(idTokenPayload)?.groupValues?.get(1)?.toBoolean()
        return userinfo["email_verified"] to idTokenValue
    }

    describe("email_verified — 세 경로가 같은 실제 값") {

        it("인증 메일을 두 번 요청하고 인증하지 않으면 셋 다 false") {
            val user = restTemplate.createAndLoginUser(resources, "e2e_emailv_")
            requestVerificationTwice(user)

            profileEmailVerified(user.accessToken) shouldBe false
            oidcEmailVerified(user) shouldBe (false to false)
        }

        it("메일 링크로 인증하면 셋 다 true") {
            val user = restTemplate.createAndLoginUser(resources, "e2e_emailv_")
            requestVerificationTwice(user)
            val token = Regex("token=([A-Za-z0-9_-]+)").find(demoInbox.latest(emailOf(user)).shouldNotBeNull().message.body).shouldNotBeNull().groupValues[1]
            restTemplate.postJson<String>("/user/email/verify", EmailVerificationConfirmDto(token = token)).statusCode shouldBe HttpStatus.OK

            profileEmailVerified(user.accessToken) shouldBe true
            oidcEmailVerified(user) shouldBe (true to true)
        }
    }
})