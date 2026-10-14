package com.rex.api.pub.skeleton.admin

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.dto.auth.AssignRolesToUserRequestDto
import com.rex.api.pub.skeleton.dto.auth.AssignUsersToRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.loginSeedAccount
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
 * Admin User API E2E 테스트.
 *
 * 사용자 권한 관리 API를 실제 HTTP 요청으로 테스트합니다.
 * - 사용자의 그룹/권한 조회
 * - 사용자에게 그룹 할당/해제
 * - 권한/그룹 보유 여부 확인
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.admin.AdminUserE2ETest"
 * ```
 *
 * 참고: Admin API는 SUPER_ADMIN 또는 ADMIN 권한이 필요합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AdminUserE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AdminUserE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_user_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_user_${UUID.randomUUID().toString().take(8)}@example.com"
    fun uniqueGroupCode() = "E2E_UGRP_${UUID.randomUUID().toString().take(6).uppercase()}"

    // 일반 사용자 생성 및 로그인
    // (accessToken, userId) — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): Pair<String, Long> =
        restTemplate.createAndLoginUser(resources, "e2e_user_").let { it.accessToken to it.userId }

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    // 그룹 생성 헬퍼
    fun createGroup(adminToken: String): Long {
        val createRequest = CreateRoleRequestDto(
            code = uniqueGroupCode(),
            name = "E2E 테스트 그룹",
        )
        val response = restTemplate.postWithAuth<Map<String, Any>>(
            "/admin/groups",
            adminToken,
            createRequest,
        )
        response.body?.get("id")?.let { resources.group((it as Number).toLong()) }
        response.statusCode shouldBe HttpStatus.CREATED
        return (response.body.shouldNotBeNull()["id"] as Number).toLong()
    }

    describe("GET /admin/users/{userId}/groups - 사용자의 그룹 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 그룹 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/users/$userId/groups",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("일반 사용자 권한으로 조회") {
            it("403 Forbidden을 반환한다") {
                // given
                val (userToken, userId) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/users/$userId/groups",
                    userToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    describe("GET /admin/users/{userId}/groups/detail - 사용자의 그룹 매핑 상세 조회") {

        context("Admin 권한으로 상세 조회") {
            it("200 OK와 그룹 매핑 상세 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/users/$userId/groups/detail",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("GET /admin/users/{userId}/permissions - 사용자의 권한 목록 조회") {

        context("Admin 권한으로 조회") {
            it("200 OK와 권한 목록을 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<String>(
                    "/admin/users/$userId/permissions",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("POST /admin/users/{userId}/groups - 사용자에게 그룹 할당") {

        context("사용자에게 그룹 할당") {
            it("201 Created와 할당 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()
                val groupId = createGroup(adminToken)

                val request = AssignRolesToUserRequestDto(
                    roleIds = listOf(groupId),
                )

                // when
                val response = restTemplate.postWithAuth<List<Map<String, Any>>>(
                    "/admin/users/$userId/groups",
                    adminToken,
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("PUT /admin/users/{userId}/groups - 사용자의 그룹 동기화") {

        context("사용자의 그룹 동기화 (기존 그룹 제거 후 새로 할당)") {
            it("200 OK와 동기화된 정보를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()
                val groupId = createGroup(adminToken)

                // 먼저 그룹 할당
                val assignRequest = AssignRolesToUserRequestDto(roleIds = listOf(groupId))
                restTemplate.postWithAuth<List<Map<String, Any>>>(
                    "/admin/users/$userId/groups",
                    adminToken,
                    assignRequest,
                )

                // 새 그룹 생성
                val newGroupId = createGroup(adminToken)

                val syncRequest = AssignRolesToUserRequestDto(roleIds = listOf(newGroupId))

                // when
                val response = restTemplate.putWithAuth<List<Map<String, Any>>>(
                    "/admin/users/$userId/groups",
                    adminToken,
                    syncRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }
    }

    describe("DELETE /admin/users/{userId}/groups/{groupId} - 사용자에게서 그룹 제거") {

        context("사용자에게서 그룹 제거") {
            it("204 No Content를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()
                val groupId = createGroup(adminToken)

                // 그룹 할당
                val request = AssignRolesToUserRequestDto(roleIds = listOf(groupId))
                restTemplate.postWithAuth<List<Map<String, Any>>>(
                    "/admin/users/$userId/groups",
                    adminToken,
                    request,
                )

                // when
                val response = restTemplate.deleteWithAuth<Void>(
                    "/admin/users/$userId/groups/$groupId",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.NO_CONTENT
            }
        }
    }

    describe("GET /admin/users/{userId}/permissions/{permissionCode}/check - 권한 보유 여부 확인") {

        context("권한 보유 여부 확인") {
            it("200 OK와 hasPermission 결과를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<Map<String, Boolean>>(
                    "/admin/users/$userId/permissions/user:read/check",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!.containsKey("hasPermission") shouldBe true
            }
        }
    }

    describe("GET /admin/users/{userId}/groups/{groupCode}/check - 그룹 소속 여부 확인") {

        context("그룹 소속 여부 확인") {
            it("200 OK와 hasGroup 결과를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val (_, userId) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<Map<String, Boolean>>(
                    "/admin/users/$userId/groups/USER/check",
                    adminToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!.containsKey("hasGroup") shouldBe true
            }
        }
    }

    describe("인증 및 권한 검증") {

        context("인증 없이 Admin User API 호출") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/admin/users/1/groups", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("ADMIN 권한으로 그룹 배정 변경 시도 — 권한 상승 경로") {
            it("조회는 되지만 배정·동기화·제거는 403 Forbidden 이다") {
                // given — ADMIN 시드 계정(SUPER_ADMIN 아님)
                val adminOnlyToken = restTemplate.loginSeedAccount(E2ETestDataSetup.TEST_ADMIN_LOGIN_ID)
                val superAdminToken = getAdminToken()
                val (_, targetUserId) = createAndLoginUser()
                val superAdminGroupId =
                    restTemplate.getWithAuth<List<Map<String, Any>>>("/admin/groups", superAdminToken)
                        .body.shouldNotBeNull().first { it["code"] == "SUPER_ADMIN" }["id"].toString().toDouble().toLong()

                // when · then — 조회는 ADMIN 도 가능
                restTemplate.getWithAuth<String>("/admin/users/$targetUserId/groups", adminOnlyToken)
                    .statusCode shouldBe HttpStatus.OK

                // ADMIN 이 임의의 그룹(여기서는 SUPER_ADMIN)을 배정할 수 있으면 권한 상승이다
                restTemplate.postWithAuth<String>(
                    "/admin/users/$targetUserId/groups",
                    adminOnlyToken,
                    AssignRolesToUserRequestDto(roleIds = listOf(superAdminGroupId)),
                ).statusCode shouldBe HttpStatus.FORBIDDEN

                restTemplate.putWithAuth<String>(
                    "/admin/users/$targetUserId/groups",
                    adminOnlyToken,
                    AssignRolesToUserRequestDto(roleIds = listOf(superAdminGroupId)),
                ).statusCode shouldBe HttpStatus.FORBIDDEN

                restTemplate.deleteWithAuth<String>(
                    "/admin/users/$targetUserId/groups/$superAdminGroupId",
                    adminOnlyToken,
                ).statusCode shouldBe HttpStatus.FORBIDDEN

                // 그룹 쪽 역방향 경로도 같다
                restTemplate.postWithAuth<String>(
                    "/admin/groups/$superAdminGroupId/users",
                    adminOnlyToken,
                    AssignUsersToRoleRequestDto(userIds = listOf(targetUserId)),
                ).statusCode shouldBe HttpStatus.FORBIDDEN

                restTemplate.deleteWithAuth<String>(
                    "/admin/groups/$superAdminGroupId/users/$targetUserId",
                    adminOnlyToken,
                ).statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }
})