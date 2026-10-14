package com.rex.api.pub.skeleton.controller.admin

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.service.mapping.UserAccessMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

/**
 * AdminUserController 통합 테스트.
 *
 * 사용자 권한 관리 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.admin.AdminUserControllerTest"
 * ```
 *
 * 참고: 보안 필터 비활성화 (addFilters = false)하여 컨트롤러 로직만 테스트합니다.
 * 인증/인가 통합 테스트는 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [AdminUserController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AdminUserControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var userAccessMappingService: UserAccessMappingService

    init {
        // ============================================
        // 사용자의 그룹 목록 조회
        // ============================================
        Given("GET /admin/users/{userId}/groups - 사용자의 그룹 목록 조회 API") {
            val targetUserId = 10L

            When("그룹 목록 조회 요청") {
                val now = LocalDateTime.now()
                val expectedGroups = listOf(
                    GroupModel(id = 1, code = "USER", name = "일반 사용자", description = null, isSystem = false, isDefault = true, createdAt = now, updatedAt = now),
                    GroupModel(id = 2, code = "EDITOR", name = "편집자", description = null, isSystem = false, isDefault = false, createdAt = now, updatedAt = now),
                )
                every { userAccessMappingService.getUserGroups(targetUserId) } returns expectedGroups

                Then("200 OK와 그룹 목록을 반환한다") {
                    mockMvc.get("/admin/users/$targetUserId/groups") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.length()") { value(2) }
                        jsonPath("$[0].code") { value("USER") }
                        jsonPath("$[1].code") { value("EDITOR") }
                    }

                    verify(exactly = 1) { userAccessMappingService.getUserGroups(targetUserId) }
                }
            }
        }

        // ============================================
        // 사용자의 권한 목록 조회
        // ============================================
        Given("GET /admin/users/{userId}/permissions - 사용자의 권한 목록 조회 API") {
            val targetUserId = 10L

            When("권한 목록 조회 요청") {
                val now = LocalDateTime.now()
                val expectedPermissions = listOf(
                    PermissionModel(
                        id = 1,
                        code = "admin:user:read",
                        name = "사용자 조회",
                        description = null,
                        resourceType = "user",
                        action = "read",
                        isSystem = false,
                        createdAt = now,
                    ),
                    PermissionModel(
                        id = 2,
                        code = "content:edit",
                        name = "컨텐츠 편집",
                        description = null,
                        resourceType = "content",
                        action = "edit",
                        isSystem = false,
                        createdAt = now,
                    ),
                )
                every { userAccessMappingService.getUserPermissions(targetUserId) } returns expectedPermissions

                Then("200 OK와 권한 목록을 반환한다") {
                    mockMvc.get("/admin/users/$targetUserId/permissions") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.length()") { value(2) }
                        jsonPath("$[0].code") { value("admin:user:read") }
                    }
                }
            }
        }

        // ============================================
        // 인증이 필요한 엔드포인트 - E2E 테스트에서 검증
        // ============================================
        // 아래 엔드포인트들은 @AuthenticationPrincipal을 사용하므로
        // @WebMvcTest에서 security filter 비활성화 시 인증 정보가 null이 됩니다.
        // E2E 테스트에서 전체 인증 흐름과 함께 테스트합니다.
        //
        // POST /admin/users/{userId}/groups - 사용자에게 그룹 할당
        // PUT /admin/users/{userId}/groups - 사용자의 그룹 동기화

        // ============================================
        // 사용자에게서 그룹 제거
        // ============================================
        Given("DELETE /admin/users/{userId}/groups/{groupId} - 사용자에게서 그룹 제거 API") {
            val targetUserId = 10L
            val groupId = 2L

            When("그룹 제거 요청") {
                justRun { userAccessMappingService.removeGroupFromUser(targetUserId, groupId) }

                Then("204 No Content를 반환한다") {
                    mockMvc.delete("/admin/users/$targetUserId/groups/$groupId") {}
                        .andExpect { status { isNoContent() } }

                    verify(exactly = 1) { userAccessMappingService.removeGroupFromUser(targetUserId, groupId) }
                }
            }
        }

        // ============================================
        // 사용자 권한 확인
        // ============================================
        Given("GET /admin/users/{userId}/permissions/{permissionCode}/check - 사용자 권한 확인 API") {
            val targetUserId = 10L
            val permissionCode = "admin:user:read"

            When("권한 확인 요청") {
                every { userAccessMappingService.hasPermission(targetUserId, permissionCode) } returns true

                Then("200 OK와 hasPermission 결과를 반환한다") {
                    mockMvc.get("/admin/users/$targetUserId/permissions/$permissionCode/check") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.hasPermission") { value(true) }
                    }
                }
            }
        }

        // ============================================
        // 사용자 그룹 소속 확인
        // ============================================
        Given("GET /admin/users/{userId}/groups/{groupCode}/check - 사용자 그룹 소속 확인 API") {
            val targetUserId = 10L
            val groupCode = "ADMIN"

            When("그룹 소속 확인 요청") {
                every { userAccessMappingService.hasGroup(targetUserId, groupCode) } returns false

                Then("200 OK와 hasGroup 결과를 반환한다") {
                    mockMvc.get("/admin/users/$targetUserId/groups/$groupCode/check") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.hasGroup") { value(false) }
                    }
                }
            }
        }
    }
}