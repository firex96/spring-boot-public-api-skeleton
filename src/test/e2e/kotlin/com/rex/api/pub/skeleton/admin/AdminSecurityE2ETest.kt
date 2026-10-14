package com.rex.api.pub.skeleton.admin

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.security.BlockIpRequestDto
import com.rex.api.pub.skeleton.dto.security.UnlockAccountRequestDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postWithAuth
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
 * Admin Security API E2E 테스트.
 *
 * 보안 관리 API를 실제 HTTP 요청으로 테스트합니다.
 * - IP 차단 관리 (차단, 해제, 조회)
 * - 계정 잠금 관리 (잠금 해제, 상태 조회)
 * - 보안 설정 조회
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.admin.AdminSecurityE2ETest"
 * ```
 *
 * 참고: Admin API는 admin:security:manage 권한이 필요합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AdminSecurityE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AdminSecurityE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_security_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_security_${UUID.randomUUID().toString().take(8)}@example.com"
    fun uniqueIpAddress() = "192.168.${(1..254).random()}.${(1..254).random()}"

    // 일반 사용자 생성 및 로그인
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): String = restTemplate.createAndLoginUser(resources, "e2e_security_").accessToken

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    describe("GET /admin/security/blocked-ips - 차단된 IP 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 페이징된 IP 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>("/admin/security/blocked-ips", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val userToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/security/blocked-ips", userToken)

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        context("인증 없이 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/admin/security/blocked-ips", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("POST /admin/security/blocked-ips - IP 차단") {

        context("Admin 권한으로 IP 차단") {
            it("201 Created와 차단 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = BlockIpRequestDto(
                    ipAddress = uniqueIpAddress(),
                    reason = BlockReason.MANUAL,
                    description = "E2E 테스트 차단",
                    durationMinutes = 60,
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/security/blocked-ips",
                    adminToken,
                    request,
                )
                response.body?.get("ipAddress")?.let { resources.blockedIp(it as String) }

                // then
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }

        context("영구 차단 요청") {
            it("durationMinutes가 null이면 영구 차단") {
                // given
                val adminToken = getAdminToken()
                val request = BlockIpRequestDto(
                    ipAddress = uniqueIpAddress(),
                    reason = BlockReason.BRUTE_FORCE,
                    description = "영구 차단 테스트",
                    durationMinutes = null,
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/security/blocked-ips",
                    adminToken,
                    request,
                )
                response.body?.get("ipAddress")?.let { resources.blockedIp(it as String) }

                // then
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    describe("GET /admin/security/blocked-ips/check/{ipAddress} - IP 차단 상태 확인") {

        context("차단되지 않은 IP 확인") {
            it("isBlocked: false를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val ipAddress = "10.0.0.1"

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/security/blocked-ips/check/$ipAddress",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("DELETE /admin/security/blocked-ips/{id} - IP 차단 해제") {

        context("존재하지 않는 ID로 해제 시도") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.deleteWithAuth<String>(
                    "/admin/security/blocked-ips/99999999",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("DELETE /admin/security/blocked-ips/ip/{ipAddress} - IP 주소로 차단 해제") {

        context("차단되지 않은 IP 해제 시도") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val ipAddress = "1.1.1.1"

                // when
                val response = restTemplate.deleteWithAuth<String>(
                    "/admin/security/blocked-ips/ip/$ipAddress",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("POST /admin/security/accounts/unlock - 계정 잠금 해제") {

        context("Admin 권한으로 계정 잠금 해제") {
            it("200 OK와 메시지를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = UnlockAccountRequestDto(username = "testuser")

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/security/accounts/unlock",
                    adminToken,
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 요청") {
            it("403 Forbidden을 반환한다") {
                // given
                val userToken = createAndLoginUser()
                val request = UnlockAccountRequestDto(username = "testuser")

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/admin/security/accounts/unlock",
                    userToken,
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    describe("GET /admin/security/accounts/status/{username} - 계정 상태 확인") {

        context("Admin 권한으로 계정 상태 조회") {
            it("200 OK와 상태 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/security/accounts/status/testuser",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("GET /admin/security/settings - 보안 설정 조회") {

        context("Admin 권한으로 보안 설정 조회") {
            it("200 OK와 설정 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>("/admin/security/settings", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val userToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/security/settings", userToken)

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }
})