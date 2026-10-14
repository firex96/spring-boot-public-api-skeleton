package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserInfoDto
import com.rex.api.pub.skeleton.dto.security.UnlockAccountRequestDto
import com.rex.api.pub.skeleton.model.security.LoginAttemptStatusModel
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import com.rex.api.pub.skeleton.support.superAdminToken
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * 로그인 보호 집행 — 잘못된 비밀번호 5회(`security.login-protection.max-login-attempts`) 뒤 계정이 잠기고(423 `AUTH001`),
 * 관리자 조회에 `isLocked` 가 보이며, 해제 뒤 다시 로그인된다. `AuthBizService.login` 이 `LoginAttemptBizService` 를
 * 호출하지 않으면(집행 지점이 빠진 상태) 3단계에서 `200` 이 나와 실패한다.
 *
 * IP 자동 차단은 e2e 프로파일에서 꺼 둔다(한 IP 에서 수백 회 로그인하는 스위트가 자기 IP 를 차단한다) — 유닛 `AuthBizServiceLoginProtectionTest` L-1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class LoginProtectionE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] LoginProtectionE2ETest ${janitor.cleanup(resources)}") }

    val password = "Test@1234!"

    fun signup(): String {
        val loginId = "e2e_lock_${UUID.randomUUID().toString().take(8)}"
        val response =
            restTemplate.postJson<UserInfoDto>(
                "/auth/signup",
                SignupRequestDto(loginId = loginId, password = password, email = "$loginId@example.com"),
            )
        response.statusCode shouldBe HttpStatus.CREATED
        resources.user(loginId)
        return loginId
    }

    fun login(loginId: String, pw: String) =
        restTemplate.postJson<Map<String, Any>>("/auth/login", LoginRequestDto(loginId = loginId, password = pw, deviceType = "WEB"))

    fun status(adminToken: String, loginId: String): LoginAttemptStatusModel {
        val response = restTemplate.getWithAuth<LoginAttemptStatusModel>("/admin/security/accounts/status/$loginId", adminToken)
        response.statusCode shouldBe HttpStatus.OK
        return response.body.shouldNotBeNull()
    }

    describe("로그인 보호 — 계정 잠금") {

        it("잠금 전 성공은 실패 카운터를 지운다 — 4회 실패 뒤 성공 → currentAttempts 0") {
            val adminToken = restTemplate.superAdminToken()
            val loginId = signup()

            repeat(4) { login(loginId, "wrong-$it").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            login(loginId, password).statusCode shouldBe HttpStatus.OK

            val after = status(adminToken, loginId)
            after.currentAttempts shouldBe 0
            after.isLocked shouldBe false
        }

        it("5회 실패 뒤 올바른 비밀번호도 423 AUTH001 — 관리자 조회 isLocked=true, 해제 뒤 200") {
            val adminToken = restTemplate.superAdminToken()
            val loginId = signup()

            // 3단계: 5회 실패 → 잠금
            repeat(5) { login(loginId, "wrong-$it").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            val locked = login(loginId, password)
            locked.statusCode shouldBe HttpStatus.LOCKED
            val body = locked.body.shouldNotBeNull()
            body["divisionCode"] shouldBe "AUTH001"
            (body["status"] as Number).toInt() shouldBe 423

            // 4단계: 관리자 조회 — is-prefix Boolean 이 HTTP 로 그대로 내려온다
            val status = status(adminToken, loginId)
            status.isLocked shouldBe true
            status.currentAttempts shouldBeInRange 5..6
            status.remainingLockTimeSeconds.shouldNotBeNull() shouldBeInRange 1L..1800L

            // 5단계: 해제 → 로그인 200 → 상태 초기화
            restTemplate.postWithAuth<Map<String, Any>>(
                "/admin/security/accounts/unlock",
                adminToken,
                UnlockAccountRequestDto(username = loginId),
            ).statusCode shouldBe HttpStatus.OK
            val tokens = restTemplate.postJson<TokenResponseDto>("/auth/login", LoginRequestDto(loginId = loginId, password = password))
            tokens.statusCode shouldBe HttpStatus.OK

            val unlocked = status(adminToken, loginId)
            unlocked.isLocked shouldBe false
            unlocked.currentAttempts shouldBe 0
        }
    }
})