package com.rex.api.pub.skeleton.admin

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.AssignPermissionsToGroupRequestDto
import com.rex.api.pub.skeleton.dto.auth.AssignUsersToRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdateRoleRequestDto
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
 * Admin Group API E2E 테스트.
 *
 * 그룹 관리 API를 실제 HTTP 요청으로 테스트합니다.
 * - 그룹 CRUD
 * - 그룹-권한 관리
 * - 그룹-사용자 관리
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.admin.AdminGroupE2ETest"
 * ```
 *
 * 참고: Admin API는 SUPER_ADMIN 또는 ADMIN 권한이 필요합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AdminGroupE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AdminGroupE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_group_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_group_${UUID.randomUUID().toString().take(8)}@example.com"
    fun uniqueGroupCode() = "E2E_GRP_${UUID.randomUUID().toString().take(6).uppercase()}"

    // 일반 사용자 생성 및 로그인
    // (accessToken, userId) — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): Pair<String, Long> =
        restTemplate.createAndLoginUser(resources, "e2e_group_").let { it.accessToken to it.userId }

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    describe("GET /admin/groups - 전체 그룹 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 그룹 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/groups", adminToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val (userToken, _) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>("/admin/groups", userToken)

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        context("인증 없이 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/admin/groups", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("POST /admin/groups - 그룹 생성") {

        context("SUPER_ADMIN 권한으로 그룹 생성") {
            it("201 Created와 그룹 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val request = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "E2E 테스트 그룹",
                    description = "E2E 테스트용 그룹입니다",
                    isDefault = false,
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    request,
                )
                response.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                // then
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }

        context("필수 필드 누락으로 생성 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val invalidRequest = mapOf(
                    "description" to "코드와 이름 없는 그룹",
                )

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/admin/groups",
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
                val request = CreateRoleRequestDto(
                    code = "invalid-code", // 소문자, 하이픈 사용 (잘못된 형식)
                    name = "잘못된 코드 그룹",
                )

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/admin/groups",
                    adminToken,
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("GET /admin/groups/{id} - 그룹 상세 조회") {

        context("존재하는 그룹 조회") {
            it("200 OK와 그룹 상세 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "상세 조회 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/admin/groups/$groupId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }

        context("존재하지 않는 그룹 조회") {
            it("404 Not Found를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/groups/99999",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    describe("PUT /admin/groups/{id} - 그룹 수정") {

        context("그룹 정보 수정") {
            it("200 OK와 수정된 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "수정 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                val updateRequest = UpdateRoleRequestDto(
                    name = "수정된 그룹 이름",
                    description = "수정된 설명",
                )

                // when
                val response = restTemplate.putWithAuth<Map<String, Any>>(
                    "/admin/groups/$groupId",
                    adminToken,
                    updateRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["name"] shouldBe "수정된 그룹 이름"
            }
        }
    }

    describe("DELETE /admin/groups/{id} - 그룹 삭제") {

        context("그룹 삭제") {
            it("204 No Content를 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "삭제 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.deleteWithAuth<Void>(
                    "/admin/groups/$groupId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NO_CONTENT
            }
        }
    }

    describe("GET /admin/groups/{id}/permissions - 그룹의 권한 목록 조회") {

        context("그룹의 권한 목록 조회") {
            it("200 OK와 권한 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "권한 조회 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/groups/$groupId/permissions",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("GET /admin/groups/{id}/users - 그룹에 속한 사용자 목록 조회") {

        context("그룹의 사용자 목록 조회") {
            it("200 OK와 사용자 ID 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "사용자 조회 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/groups/$groupId/users",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("POST /admin/groups/{id}/users - 그룹에 사용자 할당") {

        context("그룹에 사용자 할당") {
            it("201 Created와 할당 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "사용자 할당 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                val assignRequest = AssignUsersToRoleRequestDto(
                    userIds = listOf(userId),
                )

                // when
                val response = restTemplate.postWithAuth<List<Map<String, Any>>>(
                    "/admin/groups/$groupId/users",
                    adminToken,
                    assignRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("DELETE /admin/groups/{groupId}/permissions/{permissionId} - 그룹에서 권한 회수") {

        context("할당된 권한을 회수") {
            it("204 뒤 GET 목록에 권한이 없다 (회수가 실제로 일어난다)") {
                // given
                val adminToken = getAdminToken()
                val group = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    CreateRoleRequestDto(code = uniqueGroupCode(), name = "권한 회수 테스트 그룹"),
                )
                group.statusCode shouldBe HttpStatus.CREATED
                val groupId = resources.group((group.body.shouldNotBeNull()["id"] as Number).toLong())

                val permission = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/permissions",
                    adminToken,
                    CreatePermissionRequestDto(code = "e2egrp${UUID.randomUUID().toString().take(4)}:revoke", name = "회수 테스트 권한"),
                )
                permission.statusCode shouldBe HttpStatus.CREATED
                val permissionId = resources.permission((permission.body.shouldNotBeNull()["id"] as Number).toLong())

                val assign = restTemplate.postWithAuth<List<Map<String, Any>>>(
                    "/admin/groups/$groupId/permissions",
                    adminToken,
                    AssignPermissionsToGroupRequestDto(permissionIds = listOf(permissionId)),
                )
                assign.statusCode shouldBe HttpStatus.CREATED

                // when
                val response = restTemplate.deleteWithAuth<Void>("/admin/groups/$groupId/permissions/$permissionId", adminToken)

                // then — 204 만으로는 부족하다(파생 deleteBy… 조용한 무효)
                response.statusCode shouldBe HttpStatus.NO_CONTENT
                val remaining = restTemplate.getWithAuth<List<Map<String, Any>>>("/admin/groups/$groupId/permissions", adminToken)
                remaining.statusCode shouldBe HttpStatus.OK
                remaining.body.shouldNotBeNull().none { (it["id"] as Number).toLong() == permissionId } shouldBe true
            }
        }
    }

    describe("DELETE /admin/groups/{groupId}/users/{userId} - 그룹에서 사용자 제거") {

        context("그룹에서 사용자 제거") {
            it("204 No Content를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // 그룹 먼저 생성
                val createRequest = CreateRoleRequestDto(
                    code = uniqueGroupCode(),
                    name = "사용자 제거 테스트 그룹",
                )
                val createResponse = restTemplate.postWithAuth<Map<String, Any>>(
                    "/admin/groups",
                    adminToken,
                    createRequest,
                )
                createResponse.body?.get("id")?.let { resources.group((it as Number).toLong()) }

                createResponse.statusCode shouldBe HttpStatus.CREATED
                val groupId = (createResponse.body!!["id"] as Number).toLong()

                // 사용자 할당
                val assignRequest = AssignUsersToRoleRequestDto(userIds = listOf(userId))
                restTemplate.postWithAuth<List<Map<String, Any>>>(
                    "/admin/groups/$groupId/users",
                    adminToken,
                    assignRequest,
                )

                // when
                val response = restTemplate.deleteWithAuth<Void>(
                    "/admin/groups/$groupId/users/$userId",
                    adminToken,
                )

                // then — 204 만으로는 부족하다: 파생 deleteBy… 가 다른 트랜잭션 매니저에 묶이면 204 를 돌려주며 조용히 무효였다
                response.statusCode shouldBe HttpStatus.NO_CONTENT
                val remaining = restTemplate.getWithAuth<List<Map<String, Any>>>("/admin/users/$userId/groups", adminToken)
                remaining.statusCode shouldBe HttpStatus.OK
                remaining.body.shouldNotBeNull().none { (it["id"] as Number).toLong() == groupId } shouldBe true
            }
        }
    }
})