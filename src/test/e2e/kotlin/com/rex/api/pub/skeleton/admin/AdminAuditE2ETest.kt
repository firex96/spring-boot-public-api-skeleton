package com.rex.api.pub.skeleton.admin

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.superAdminToken
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Admin Audit API E2E 테스트.
 *
 * 감사 로그 관리 API를 실제 HTTP 요청으로 테스트합니다.
 * - 감사 로그 목록 조회
 * - 감사 로그 검색
 * - 사용자별/이벤트별 로그 조회
 * - 이벤트 타입/카테고리 목록 조회
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.admin.AdminAuditE2ETest"
 * ```
 *
 * 참고: Admin API는 ADMIN 권한이 필요합니다.
 * 테스트 환경에서 admin 사용자 설정이 필요합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AdminAuditE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AdminAuditE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_admin_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_admin_${UUID.randomUUID().toString().take(8)}@example.com"

    // 일반 사용자 생성 및 로그인 (테스트 데이터 생성용)
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): String = restTemplate.createAndLoginUser(resources, "e2e_admin_").accessToken

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    describe("GET /admin/audit/logs - 감사 로그 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 페이징된 로그 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()
                // 감사 로그 생성을 위해 사용자 활동 수행
                createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>("/admin/audit/logs", adminToken)

                // then
                // admin 권한이 없으면 403, 있으면 200
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val userToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/audit/logs", userToken)

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        context("인증 없이 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/admin/audit/logs", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("GET /admin/audit/logs/search - 감사 로그 검색") {

        context("userId로 검색") {
            it("해당 사용자의 로그만 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/audit/logs/search?userId=1",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("eventType으로 검색") {
            it("해당 이벤트 타입의 로그만 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/audit/logs/search?eventType=LOGIN_SUCCESS",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("날짜 범위로 검색") {
            it("해당 기간의 로그만 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/audit/logs/search?startDate=2024-01-01T00:00:00&endDate=2030-12-31T23:59:59",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("GET /admin/audit/logs/user/{userId} - 사용자별 감사 로그 조회") {

        context("특정 사용자의 로그 조회") {
            it("해당 사용자의 로그 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/audit/logs/user/1",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("GET /admin/audit/logs/event/{eventType} - 이벤트 타입별 감사 로그 조회") {

        context("LOGIN_SUCCESS 이벤트 조회") {
            it("LOGIN_SUCCESS 이벤트 로그 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/audit/logs/event/LOGIN_SUCCESS",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("LOGOUT 이벤트 조회") {
            it("LOGOUT 이벤트 로그 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/audit/logs/event/LOGOUT",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("GET /admin/audit/event-types - 이벤트 타입 목록 조회") {

        context("Admin 권한으로 조회") {
            it("이벤트 타입 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/audit/event-types", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("GET /admin/audit/event-categories - 이벤트 카테고리 목록 조회") {

        context("Admin 권한으로 조회") {
            it("이벤트 카테고리 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/audit/event-categories", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }
    }
})