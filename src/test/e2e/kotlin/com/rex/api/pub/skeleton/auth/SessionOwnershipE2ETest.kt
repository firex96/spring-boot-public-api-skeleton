package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.E2eUser
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.util.Base64
import java.util.UUID

/**
 * `DELETE /users/me/sessions/{sessionId}` 의 인가 계약 — **요청자 소유 세션만** 폐기한다.
 *
 * sessionId 는 액세스 토큰의 `jti` 다(`GET /user/sessions` 는 마스킹). 타인이 그 값으로 세션을 종료할 수 있으면 IDOR 이다.
 * O-1 타인 세션 → 403 이고 피해자 세션은 살아 있다 · O-2 자기 세션 → 204 뒤 그 토큰은 401 · O-3 없는 세션 → 204(멱등).
 * O-4·O-5 일괄 폐기(전체 로그아웃·비밀번호 변경) 뒤 폐기된 세션의 토큰은 캐시가 있어도 바로 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class SessionOwnershipE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({
    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] SessionOwnershipE2ETest ${janitor.cleanup(resources)}") }

    // sessionId 는 액세스 토큰의 jti 클레임이다 — GET /user/sessions 는 마스킹(8자 + ****)해서 준다
    fun currentSessionIdOf(user: E2eUser): String {
        val payload = String(Base64.getUrlDecoder().decode(user.accessToken.split(".")[1]), Charsets.UTF_8)
        return Regex(""""jti"\s*:\s*"([^"]+)"""").find(payload).shouldNotBeNull().groupValues[1]
    }

    describe("DELETE /users/me/sessions/{sessionId} — 소유권") {

        context("O-1 다른 사용자의 sessionId 로 종료를 시도하면") {
            it("403 이고 피해자의 세션은 살아 있다") {
                val victim: E2eUser = restTemplate.createAndLoginUser(resources, "e2e_sess_")
                val attacker: E2eUser = restTemplate.createAndLoginUser(resources, "e2e_sess_")
                val victimSession = currentSessionIdOf(victim)

                val response = restTemplate.deleteWithAuth<String>("/users/me/sessions/$victimSession", attacker.accessToken)

                response.statusCode shouldBe HttpStatus.FORBIDDEN
                response.body.shouldNotBeNull() shouldContain "G008"
                restTemplate.getWithAuth<String>("/user/profile", victim.accessToken).statusCode shouldBe HttpStatus.OK
            }
        }

        context("O-2 자기 sessionId 로 종료하면") {
            it("204 이고 그 토큰은 더 쓸 수 없다") {
                val owner: E2eUser = restTemplate.createAndLoginUser(resources, "e2e_sess_")
                val ownSession = currentSessionIdOf(owner)

                val response = restTemplate.deleteWithAuth<String>("/users/me/sessions/$ownSession", owner.accessToken)

                response.statusCode shouldBe HttpStatus.NO_CONTENT
                restTemplate.getWithAuth<String>("/user/profile", owner.accessToken).statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("O-3 존재하지 않는 sessionId 로 종료하면") {
            it("204 (멱등) 이다") {
                val user: E2eUser = restTemplate.createAndLoginUser(resources, "e2e_sess_")

                val response = restTemplate.deleteWithAuth<String>("/users/me/sessions/${UUID.randomUUID()}", user.accessToken)

                response.statusCode shouldBe HttpStatus.NO_CONTENT
            }
        }
    }

    describe("일괄 폐기 — 폐기된 세션의 토큰은 바로 401") {

        /** 같은 사용자의 두 번째 로그인(다른 기기) — 세션이 하나 더 생긴다. */
        fun loginAgain(user: E2eUser): String =
            restTemplate.postJson<Map<String, Any>>("/auth/login", LoginRequestDto(loginId = user.loginId, password = E2ETestDataSetup.DEFAULT_TEST_PASSWORD))
                .body.shouldNotBeNull()["accessToken"] as String

        context("O-4 전체 로그아웃(DELETE /users/me/sessions)") {
            it("다른 세션의 토큰은 401, 요청한 세션은 살아 있다 — 세션 캐시가 남아 통과하면 안 된다") {
                val user: E2eUser = restTemplate.createAndLoginUser(resources, "e2e_sess_")
                val otherDevice = loginAgain(user)
                // 다른 기기 세션을 한 번 써서 캐시에 올린다 — 캐시가 있어야 결함이 드러난다
                restTemplate.getWithAuth<String>("/user/profile", otherDevice).statusCode shouldBe HttpStatus.OK

                restTemplate.deleteWithAuth<String>("/users/me/sessions", user.accessToken).statusCode.is2xxSuccessful shouldBe true

                restTemplate.getWithAuth<String>("/user/profile", otherDevice).statusCode shouldBe HttpStatus.UNAUTHORIZED
                restTemplate.getWithAuth<String>("/user/profile", user.accessToken).statusCode shouldBe HttpStatus.OK
            }
        }

        context("O-5 비밀번호 변경(POST /auth/password/change)") {
            it("모든 세션의 토큰이 401 이다") {
                val user: E2eUser = restTemplate.createAndLoginUser(resources, "e2e_sess_")
                val otherDevice = loginAgain(user)
                restTemplate.getWithAuth<String>("/user/profile", otherDevice).statusCode shouldBe HttpStatus.OK

                restTemplate.postWithAuth<String>(
                    "/auth/password/change",
                    user.accessToken,
                    ChangePasswordRequestDto(currentPassword = E2ETestDataSetup.DEFAULT_TEST_PASSWORD, newPassword = "Changed@5678!"),
                ).statusCode.is2xxSuccessful shouldBe true

                restTemplate.getWithAuth<String>("/user/profile", otherDevice).statusCode shouldBe HttpStatus.UNAUTHORIZED
                restTemplate.getWithAuth<String>("/user/profile", user.accessToken).statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }
})