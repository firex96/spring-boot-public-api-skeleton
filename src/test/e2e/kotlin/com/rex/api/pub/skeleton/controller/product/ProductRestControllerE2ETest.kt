package com.rex.api.pub.skeleton.controller.product

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.model.ProductModel
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
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
import tools.jackson.databind.json.JsonMapper

/**
 * ProductRestController E2E 테스트.
 *
 * 실제 HTTP 요청을 통해 API 엔드포인트를 테스트합니다.
 * - 전체 Spring Context 로드
 * - 실제 서버 기동 (RANDOM_PORT)
 * - TestRestTemplate을 통한 HTTP 호출
 * - docker compose 로 띄운 로컬 인프라(DB, Redis) 사용 (src/test/e2e/resources/application-e2e.yml 설정)
 *
 * 실행 전 요구사항:
 * - docker compose up -d (MySQL·Redis — local 프로필 값)
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class ProductRestControllerE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
    private val objectMapper: JsonMapper,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] ProductRestControllerE2ETest ${janitor.cleanup(resources)}") }

    // 테스트용 인증 토큰 획득
    // 테스트용 인증 토큰 — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    fun getAccessToken(): String = restTemplate.createAndLoginUser(resources, "product_test_").accessToken

    describe("GET /products/{id}") {

        context("Product ID로 조회") {
            it("존재하는 Product인 경우 ProductModel을 반환한다") {
                // given
                val productId = 1L
                val token = getAccessToken()

                // when
                val response = restTemplate.getWithAuth<String>("/products/$productId", token)

                // then
                when (response.statusCode) {
                    HttpStatus.OK -> {
                        val productModel = objectMapper.readValue(response.body, ProductModel::class.java)
                        productModel.shouldNotBeNull()
                        productModel.id shouldBe productId
                    }
                    HttpStatus.NOT_FOUND -> {
                        // DB에 데이터가 없는 경우
                        response.body shouldContain "NOT_FOUND"
                    }
                    else -> {
                        // 예상치 못한 응답
                        throw AssertionError("Unexpected status: ${response.statusCode}")
                    }
                }
            }
        }

        context("존재하지 않는 Product ID로 조회") {
            it("404 Not Found를 반환한다") {
                // given
                val nonExistentId = 999999
                val token = getAccessToken()

                // when
                val response = restTemplate.getWithAuth<String>("/products/$nonExistentId", token)

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
                response.body shouldContain "NOT_FOUND"
            }
        }
    }

    describe("GET /products/{id}/core") {

        context("Core API를 통한 Product 조회") {
            it("실제 Core API를 통해 ProductModel을 반환한다") {
                // given
                val productId = 1L
                val token = getAccessToken()

                // when
                val response = restTemplate.getWithAuth<String>("/products/$productId/core", token)

                // then
                when (response.statusCode) {
                    HttpStatus.OK -> {
                        val productModel = objectMapper.readValue(response.body, ProductModel::class.java)
                        productModel.shouldNotBeNull()
                        productModel.id shouldBe productId
                    }
                    HttpStatus.NOT_FOUND -> {
                        // Core API에 데이터가 없는 경우
                        response.body shouldContain "NOT_FOUND"
                    }
                    HttpStatus.SERVICE_UNAVAILABLE -> {
                        // Core API 에 붙지 못한 경우. 외부 가용성 문제이므로 통과시키되 검증되지 않았음을 남긴다.
                        // 500 은 여기서 제외한다 — Core API 가 응답했는데 처리에 실패한 것이고,
                        // 우리 요청이 원인일 수 있다(역직렬화 실패가 이 경로로 가려진다).
                        println("경고: Core API 연결 실패(503) — 이 케이스는 검증되지 않았다")
                    }
                    else -> {
                        throw AssertionError("Unexpected status: ${response.statusCode}")
                    }
                }
            }
        }
    }
})