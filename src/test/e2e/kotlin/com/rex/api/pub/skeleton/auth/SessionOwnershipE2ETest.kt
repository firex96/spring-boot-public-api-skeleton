package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.E2eUser
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
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
})