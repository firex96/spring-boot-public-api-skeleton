package com.rex.api.pub.skeleton.admin

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdatePermissionRequestDto
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postWithAuth
import com.rex.api.pub.skeleton.support.putWithAuth
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
 * Admin Permission API E2E 테스트.
 *
 * 권한 관리 API를 실제 HTTP 요청으로 테스트합니다.
 * - 권한 CRUD
 * - 리소스 타입별 권한 조회
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.admin.AdminPermissionE2ETest"
 * ```
 *
 * 참고: Admin Permission API는 SUPER_ADMIN 권한이 필요합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AdminPermissionE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AdminPermissionE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_perm_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_perm_${UUID.randomUUID().toString().take(8)}@example.com"
    fun uniquePermissionCode() = "e2e${UUID.randomUUID().toString().take(4)}:test"

    // 일반 사용자 생성 및 로그인
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): String = restTemplate.createAndLoginUser(resources, "e2e_perm_").accessToken

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    describe("GET /admin/permissions - 전체 권한 목록 조회") {

        context("SUPER_ADMIN 권한으로 조회") {
            it("200 OK와 권한 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/permissions", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val userToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/permissions", userToken)

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        context("인증 없이 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/admin/permissions", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("POST /admin/permissions - 권한 생성") {

        context("SUPER_ADMIN 권한으로 권한 생성") {
            it("201 Created와 권한 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = CreatePermissionRequestDto(
                    code = uniquePermissionCode(),
                    name = "E2E 테스트 권한",
                    description = "E2E 테스트용 권한입니다",
                    resourceType = "e2etest",
                    action = "test",
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/permissions",
                    adminToken,
                    request,
                )
                response.body?.get("id")?.let { resources.permission((it as Number).toLong()) }

                // then
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }

        context("필수 필드 누락으로 생성 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val invalidRequest = mapOf(
                    "description" to "코드와 이름 없는 권한",
                )

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/admin/permissions",
                    adminToken,
                    invalidRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        context("잘못된 코드 형식으로 생성 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = CreatePermissionRequestDto(
                    code = "INVALID_CODE", // 대문자, 언더스코어 사용 (잘못된 형식)
                    name = "잘못된 코드 권한",
                )

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/admin/permissions",
                    adminToken,
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("GET /admin/permissions/{id} - 권한 상세 조회") {

        context("존재하는 권한 조회") {
            it("200 OK와 권한 상세 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 권한 먼저 생성
                val createRequest = CreatePermissionRequestDto(
                    code = uniquePermissionCode(),
                    name = "상세 조회 테스트 권한",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/permissions",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.permission((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val permissionId = (createResponse.body!!["id"] as Number).toInt()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/permissions/$permissionId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }

        context("존재하지 않는 권한 조회") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/permissions/99999",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("GET /admin/permissions/by-resource - 리소스 타입별 권한 조회") {

        context("리소스 타입으로 권한 조회") {
            it("200 OK와 해당 리소스 타입의 권한 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/permissions/by-resource?resourceType=user",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("PUT /admin/permissions/{id} - 권한 수정") {

        context("권한 정보 수정") {
            it("200 OK와 수정된 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 권한 먼저 생성
                val createRequest = CreatePermissionRequestDto(
                    code = uniquePermissionCode(),
                    name = "수정 테스트 권한",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/permissions",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.permission((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val permissionId = (createResponse.body!!["id"] as Number).toInt()

                val updateRequest = UpdatePermissionRequestDto(
                    name = "수정된 권한 이름",
                    description = "수정된 설명",
                )

                // when
                val response = restTemplate.putWithAuth<Map<String, Any>>(
                    "/admin/permissions/$permissionId",
                    adminToken,
                    updateRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["name"] shouldBe "수정된 권한 이름"
            }
        }
    }

    describe("DELETE /admin/permissions/{id} - 권한 삭제") {

        context("권한 삭제") {
            it("204 No Content를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 권한 먼저 생성
                val createRequest = CreatePermissionRequestDto(
                    code = uniquePermissionCode(),
                    name = "삭제 테스트 권한",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/permissions",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.permission((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val permissionId = (createResponse.body!!["id"] as Number).toInt()

                // when
                val response = restTemplate.deleteWithAuth<Void>(
                    "/admin/permissions/$permissionId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NO_CONTENT

                // 삭제 후 조회 시 404
                val getResponse = restTemplate.getWithAuth<String>(
                    "/admin/permissions/$permissionId",
                    adminToken,
                )
                getResponse.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        context("존재하지 않는 권한 삭제") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.deleteWithAuth<String>(
                    "/admin/permissions/99999",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})