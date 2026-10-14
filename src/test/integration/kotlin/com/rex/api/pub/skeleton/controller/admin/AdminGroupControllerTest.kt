package com.rex.api.pub.skeleton.controller.admin

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.model.auth.GroupDetailModel
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.service.mapping.GroupMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.expectCreated
import com.rex.api.pub.skeleton.support.postJson
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
 * AdminGroupController 통합 테스트.
 *
 * 그룹 관리 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.admin.AdminGroupControllerTest"
 * ```
 *
 * 참고: 보안 필터 비활성화 (addFilters = false)하여 컨트롤러 로직만 테스트합니다.
 * 인증/인가 통합 테스트는 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [AdminGroupController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AdminGroupControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var groupMappingService: GroupMappingService

    init {
        // ============================================
        // 전체 그룹 목록 조회
        // ============================================
        Given("GET /admin/groups - 전체 그룹 목록 조회 API") {
            When("그룹 목록 조회 요청") {
                val now = LocalDateTime.now()
                val expectedGroups = listOf(
                    GroupModel(
                        id = 1,
                        code = "USER",
                        name = "일반 사용자",
                        description = "기본 사용자 그룹",
                        isSystem = false,
                        isDefault = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    GroupModel(
                        id = 2,
                        code = "ADMIN",
                        name = "관리자",
                        description = "관리자 그룹",
                        isSystem = false,
                        isDefault = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                every { groupMappingService.getAllGroups() } returns expectedGroups

                Then("200 OK와 그룹 목록을 반환한다") {
                    mockMvc.get("/admin/groups") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.length()") { value(2) }
                        jsonPath("$[0].code") { value("USER") }
                        jsonPath("$[1].code") { value("ADMIN") }
                    }
                }
            }
        }

        // ============================================
        // 그룹 상세 조회
        // ============================================
        Given("GET /admin/groups/{id} - 그룹 상세 조회 API") {
            When("그룹 상세 조회") {
                val now = LocalDateTime.now()
                val expectedDetail = GroupDetailModel(
                    id = 1,
                    code = "USER",
                    name = "일반 사용자",
                    description = "기본 사용자 그룹",
                    isSystem = false,
                    isDefault = true,
                    permissions = emptyList(),
                    userCount = 100,
                    createdAt = now,
                    updatedAt = now,
                )
                every { groupMappingService.getGroupDetail(1) } returns expectedDetail

                Then("200 OK와 그룹 상세 정보를 반환한다") {
                    mockMvc.get("/admin/groups/1") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.id") { value(1) }
                        jsonPath("$.code") { value("USER") }
                        jsonPath("$.userCount") { value(100) }
                    }
                }
            }
        }

        // ============================================
        // 그룹 생성
        // ============================================
        Given("POST /admin/groups - 그룹 생성 API") {
            val createRequest = CreateRoleRequestDto(
                code = "MANAGER",
                name = "매니저",
                description = "매니저 그룹",
                isDefault = false,
            )

            When("그룹 생성 요청") {
                val now = LocalDateTime.now()
                val expectedGroup = GroupModel(
                    id = 3,
                    code = "MANAGER",
                    name = "매니저",
                    description = "매니저 그룹",
                    isSystem = false,
                    isDefault = false,
                    createdAt = now,
                    updatedAt = now,
                )
                every { groupMappingService.createGroup(any()) } returns expectedGroup

                Then("201 Created와 생성된 그룹을 반환한다") {
                    mockMvc.postJson("/admin/groups", createRequest)
                        .expectCreated()
                        .andExpect {
                            jsonPath("$.id") { value(3) }
                            jsonPath("$.code") { value("MANAGER") }
                        }

                    verify(exactly = 1) { groupMappingService.createGroup(any()) }
                }
            }
        }

        // ============================================
        // 그룹 삭제
        // ============================================
        Given("DELETE /admin/groups/{id} - 그룹 삭제 API") {
            When("그룹 삭제 요청") {
                justRun { groupMappingService.deleteGroup(3) }

                Then("204 No Content를 반환한다") {
                    mockMvc.delete("/admin/groups/3") {}
                        .andExpect { status { isNoContent() } }

                    verify(exactly = 1) { groupMappingService.deleteGroup(3) }
                }
            }
        }

        // ============================================
        // 그룹의 권한 목록 조회
        // ============================================
        Given("GET /admin/groups/{id}/permissions - 그룹의 권한 목록 조회 API") {
            When("권한 목록 조회") {
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
                )
                every { groupMappingService.getPermissionsByGroupId(1) } returns expectedPermissions

                Then("200 OK와 권한 목록을 반환한다") {
                    mockMvc.get("/admin/groups/1/permissions") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.length()") { value(1) }
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
        // POST /admin/groups/{id}/permissions - 그룹에 권한 할당
        // POST /admin/groups/{id}/users - 그룹에 사용자 할당
        // POST /admin/groups/{id}/permissions/sync - 그룹 권한 동기화
    }
}