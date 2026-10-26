package com.rex.api.pub.skeleton.security

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.handler.ErrorReason
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.getJson
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.loginSeedAccount
import com.rex.api.pub.skeleton.support.putWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus

/**
 * 401·403 응답 형식 E2E — 실제 `SecurityConfiguration` 을 지난다.
 *
 * 필터 체인(URL 인가)에서 거부된 403 과 메서드 보안(`@PreAuthorize`)에서 거부된 403 이
 * **같은 `ErrorResponseModel` 형식**이어야 한다. 예전에는 앞의 것이 임시 Map(`{status, error, message, path}`)이었다.
 *
 * ```bash
 * ./gradlew e2eTest --tests "*.security.ErrorResponseFormatE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class ErrorResponseFormatE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] ErrorResponseFormatE2ETest ${janitor.cleanup(resources)}") }

    val errorResponseKeys = setOf("code", "reason", "errors", "status", "divisionCode", "resultMessage")

    describe("403 — 필터 체인 거부와 메서드 보안 거부") {

        it("두 경로 모두 ErrorResponseModel(FORBIDDEN_ERROR, G008) 이고 키 집합이 같다") {
            // given — 그룹 없는 일반 사용자(URL 인가 hasAnyRole 에서 거부), ADMIN 시드(클래스 인가는 통과, 메서드의 SUPER_ADMIN 에서 거부)
            val userToken = restTemplate.createAndLoginUser(resources, "e2e_errfmt_").accessToken
            val adminToken = restTemplate.loginSeedAccount(E2ETestDataSetup.TEST_ADMIN_LOGIN_ID)

            // when
            val filterChainDenied = restTemplate.getWithAuth<Map<String, Any?>>("/admin/groups", userToken)
            val methodDenied =
                restTemplate.putWithAuth<Map<String, Any?>>(
                    "/admin/groups/1",
                    adminToken,
                    mapOf("name" to "e2e-denied", "description" to "e2e"),
                )

            // then
            filterChainDenied.statusCode shouldBe HttpStatus.FORBIDDEN
            methodDenied.statusCode shouldBe HttpStatus.FORBIDDEN
            val filterBody = filterChainDenied.body.shouldNotBeNull()
            val methodBody = methodDenied.body.shouldNotBeNull()
            filterBody.keys shouldBe errorResponseKeys
            methodBody.keys shouldBe errorResponseKeys
            filterBody["code"] shouldBe "FORBIDDEN_ERROR"
            methodBody["code"] shouldBe "FORBIDDEN_ERROR"
            filterBody["divisionCode"] shouldBe "G008"
            methodBody["divisionCode"] shouldBe "G008"
            // 필터 체인과 GlobalExceptionHandler 가 같은 문장을 쓴다(ErrorReason)
            filterBody["reason"] shouldBe ErrorReason.FORBIDDEN
            methodBody["reason"] shouldBe ErrorReason.FORBIDDEN
        }
    }

    describe("401 — 인증 없이 인증 필요 API") {

        it("ErrorResponseModel(UNAUTHORIZED_ERROR, G015) 이다") {
            // when
            val response = restTemplate.getJson<Map<String, Any?>>("/users/me")

            // then
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            val body = response.body.shouldNotBeNull()
            body.keys shouldBe errorResponseKeys
            body["code"] shouldBe "UNAUTHORIZED_ERROR"
            body["divisionCode"] shouldBe "G015"
            body["reason"] shouldBe ErrorReason.UNAUTHORIZED
        }
    }
})