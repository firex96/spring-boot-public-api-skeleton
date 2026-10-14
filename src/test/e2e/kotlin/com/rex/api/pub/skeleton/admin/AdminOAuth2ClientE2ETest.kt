package com.rex.api.pub.skeleton.admin

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.UpdateOAuth2ClientRequestDto
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
 * Admin OAuth2 Client API E2E 테스트.
 *
 * OAuth2 클라이언트 관리 API를 실제 HTTP 요청으로 테스트합니다.
 * - 클라이언트 CRUD
 * - 시크릿 재생성
 * - 클라이언트 비활성화
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.admin.AdminOAuth2ClientE2ETest"
 * ```
 *
 * 참고: Admin API는 oauth2:manage 권한이 필요합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AdminOAuth2ClientE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AdminOAuth2ClientE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_oauth2_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_oauth2_${UUID.randomUUID().toString().take(8)}@example.com"
    fun uniqueClientName() = "E2E Test Client ${UUID.randomUUID().toString().take(8)}"

    // 일반 사용자 생성 및 로그인
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): String = restTemplate.createAndLoginUser(resources, "e2e_oauth2_").accessToken

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    describe("GET /admin/oauth2/clients - 전체 클라이언트 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 클라이언트 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/oauth2/clients", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val userToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/oauth2/clients", userToken)

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        context("인증 없이 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/admin/oauth2/clients", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("GET /admin/oauth2/clients/active - 활성 클라이언트 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 활성 클라이언트 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/oauth2/clients/active", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("POST /admin/oauth2/clients - 클라이언트 생성") {

        context("Admin 권한으로 클라이언트 생성") {
            it("201 Created와 클라이언트 정보(시크릿 포함)를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "E2E 테스트용 클라이언트",
                    grantTypes = listOf("authorization_code", "refresh_token"),
                    redirectUris = listOf("http://localhost:8080/callback"),
                    scopes = listOf("openid", "profile", "email"),
                    accessTokenValidity = 3600,
                    refreshTokenValidity = 604800,
                    isFirstParty = false,
                    requirePkce = true,
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    request,
                )
                response.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                // then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body.shouldNotBeNull()
                response.body!!["clientId"].shouldNotBeNull()
                response.body!!["clientSecret"].shouldNotBeNull()
            }
        }

        context("필수 필드 누락으로 생성 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val invalidRequest = mapOf(
                    "description" to "이름 없는 클라이언트",
                )

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/admin/oauth2/clients",
                    adminToken,
                    invalidRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        context("client_credentials grant type으로 생성") {
            it("201 Created와 클라이언트 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "M2M 클라이언트",
                    grantTypes = listOf("client_credentials"),
                    redirectUris = emptyList(),
                    scopes = listOf("api:read", "api:write"),
                    accessTokenValidity = 1800,
                    refreshTokenValidity = 0,
                    isFirstParty = true,
                    requirePkce = false,
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    request,
                )
                response.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                // then
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    describe("GET /admin/oauth2/clients/{id} - 클라이언트 상세 조회") {

        context("존재하는 클라이언트 조회") {
            it("200 OK와 클라이언트 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 클라이언트 먼저 생성
                val createRequest = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "조회 테스트용 클라이언트",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val clientId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients/$clientId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }

        context("존재하지 않는 클라이언트 조회") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/oauth2/clients/99999999",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("PUT /admin/oauth2/clients/{id} - 클라이언트 수정") {

        context("클라이언트 정보 수정") {
            it("200 OK와 수정된 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 클라이언트 먼저 생성
                val createRequest = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "수정 테스트용 클라이언트",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val clientId = (createResponse.body!!["id"] as Number).toLong()

                val updateRequest = UpdateOAuth2ClientRequestDto(
                    clientName = "수정된 클라이언트 이름",
                    description = "수정된 설명",
                    scopes = listOf("openid", "profile"),
                )

                // when
                val response = restTemplate.putWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients/$clientId",
                    adminToken,
                    updateRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["clientName"] shouldBe "수정된 클라이언트 이름"
            }
        }
    }

    describe("POST /admin/oauth2/clients/{id}/secret - 시크릿 재생성") {

        context("클라이언트 시크릿 재생성") {
            it("200 OK와 새 시크릿을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 클라이언트 먼저 생성
                val createRequest = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "시크릿 재생성 테스트용 클라이언트",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val clientId = (createResponse.body!!["id"] as Number).toLong()
                val originalSecret = createResponse.body!!["clientSecret"] as String

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients/$clientId/secret",
                    adminToken,
                    emptyMap<String, Any>(),
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                val newSecret = response.body!!["clientSecret"] as String
                // 새 시크릿이 발급됨 (원래와 다를 수 있음)
                newSecret.shouldNotBeNull()
            }
        }
    }

    describe("POST /admin/oauth2/clients/{id}/deactivate - 클라이언트 비활성화") {

        context("클라이언트 비활성화") {
            it("200 OK와 비활성화된 클라이언트 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 클라이언트 먼저 생성
                val createRequest = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "비활성화 테스트용 클라이언트",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val clientId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients/$clientId/deactivate",
                    adminToken,
                    emptyMap<String, Any>(),
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["isActive"] shouldBe false
            }
        }
    }

    describe("DELETE /admin/oauth2/clients/{id} - 클라이언트 삭제") {

        context("클라이언트 삭제") {
            it("204 No Content를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 클라이언트 먼저 생성
                val createRequest = CreateOAuth2ClientRequestDto(
                    clientName = uniqueClientName(),
                    description = "삭제 테스트용 클라이언트",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/oauth2/clients",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("clientId")?.let { resources.oauth2Client(it as String) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val clientId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.deleteWithAuth<Void>(
                    "/admin/oauth2/clients/$clientId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NO_CONTENT

                // 삭제 후 조회 시 404
                val getResponse = restTemplate.getWithAuth<String>(
                    "/admin/oauth2/clients/$clientId",
                    adminToken,
                )
                getResponse.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        context("존재하지 않는 클라이언트 삭제") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.deleteWithAuth<String>(
                    "/admin/oauth2/clients/99999999",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})