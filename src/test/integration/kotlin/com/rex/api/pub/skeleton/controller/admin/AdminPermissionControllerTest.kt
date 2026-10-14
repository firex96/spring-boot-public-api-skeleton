package com.rex.api.pub.skeleton.controller.admin

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdatePermissionRequestDto
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.service.mapping.PermissionMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.expectCreated
import com.rex.api.pub.skeleton.support.expectSuccess
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.putJson
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
 * AdminPermissionController 통합 테스트.
 *
 * 권한 관리 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.admin.AdminPermissionControllerTest"
 * ```
 *
 * 참고: 보안 필터 비활성화 (addFilters = false)하여 컨트롤러 로직만 테스트합니다.
 * 인증/인가 통합 테스트는 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [AdminPermissionController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AdminPermissionControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var permissionMappingService: PermissionMappingService

    init {
        // ============================================
        // 전체 권한 목록 조회
        // ============================================
        Given("GET /admin/permissions - 전체 권한 목록 조회 API") {
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
                        code = "admin:user:write",
                        name = "사용자 수정",
                        description = null,
                        resourceType = "user",
                        action = "write",
                        isSystem = false,
                        createdAt = now,
                    ),
                    PermissionModel(
                        id = 3,
                        code = "content:edit",
                        name = "컨텐츠 편집",
                        description = null,
                        resourceType = "content",
                        action = "edit",
                        isSystem = false,
                        createdAt = now,
                    ),
                )
                every { permissionMappingService.getAllPermissions() } returns expectedPermissions

                Then("200 OK와 권한 목록을 반환한다") {
                    mockMvc.get("/admin/permissions") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.length()") { value(3) }
                        jsonPath("$[0].code") { value("admin:user:read") }
                    }
                }
            }
        }

        // ============================================
        // 권한 상세 조회
        // ============================================
        Given("GET /admin/permissions/{id} - 권한 상세 조회 API") {
            When("권한 상세 조회 요청") {
                val now = LocalDateTime.now()
                val expectedPermission = PermissionModel(
                    id = 1,
                    code = "admin:user:read",
                    name = "사용자 조회",
                    description = "사용자 정보를 조회할 수 있는 권한",
                    resourceType = "user",
                    action = "read",
                    isSystem = false,
                    createdAt = now,
                )
                every { permissionMappingService.getPermission(1) } returns expectedPermission

                Then("200 OK와 권한 정보를 반환한다") {
                    mockMvc.get("/admin/permissions/1") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.id") { value(1) }
                        jsonPath("$.code") { value("admin:user:read") }
                        jsonPath("$.resourceType") { value("user") }
                    }
                }
            }
        }

        // ============================================
        // 리소스 타입별 권한 조회
        // ============================================
        Given("GET /admin/permissions/by-resource - 리소스 타입별 권한 조회 API") {
            When("user 타입 권한 조회 요청") {
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
                        code = "admin:user:write",
                        name = "사용자 수정",
                        description = null,
                        resourceType = "user",
                        action = "write",
                        isSystem = false,
                        createdAt = now,
                    ),
                )
                every { permissionMappingService.getPermissionsByResourceType("user") } returns expectedPermissions

                Then("200 OK와 user 타입 권한 목록을 반환한다") {
                    mockMvc.get("/admin/permissions/by-resource?resourceType=user") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.length()") { value(2) }
                        jsonPath("$[0].resourceType") { value("user") }
                    }
                }
            }
        }

        // ============================================
        // 권한 생성
        // ============================================
        Given("POST /admin/permissions - 권한 생성 API") {
            val createRequest = CreatePermissionRequestDto(
                code = "report:read",
                name = "리포트 조회",
                description = "리포트 조회 권한",
                resourceType = "report",
            )

            When("권한 생성 요청") {
                val now = LocalDateTime.now()
                val expectedPermission = PermissionModel(
                    id = 4,
                    code = "report:read",
                    name = "리포트 조회",
                    description = "리포트 조회 권한",
                    resourceType = "report",
                    action = "read",
                    isSystem = false,
                    createdAt = now,
                )
                every { permissionMappingService.createPermission(any()) } returns expectedPermission

                Then("201 Created와 생성된 권한을 반환한다") {
                    mockMvc.postJson("/admin/permissions", createRequest)
                        .expectCreated()
                        .andExpect {
                            jsonPath("$.id") { value(4) }
                            jsonPath("$.code") { value("report:read") }
                        }

                    verify(exactly = 1) { permissionMappingService.createPermission(any()) }
                }
            }
        }

        // ============================================
        // 권한 수정
        // ============================================
        Given("PUT /admin/permissions/{id} - 권한 수정 API") {
            val updateRequest = UpdatePermissionRequestDto(
                name = "수정된 사용자 조회",
                description = "수정된 설명",
            )

            When("권한 수정 요청") {
                val now = LocalDateTime.now()
                val expectedPermission = PermissionModel(
                    id = 1,
                    code = "admin:user:read",
                    name = "수정된 사용자 조회",
                    description = "수정된 설명",
                    resourceType = "user",
                    action = "read",
                    isSystem = false,
                    createdAt = now,
                )
                every { permissionMappingService.updatePermission(1, any()) } returns expectedPermission

                Then("200 OK와 수정된 권한을 반환한다") {
                    mockMvc.putJson("/admin/permissions/1", updateRequest)
                        .expectSuccess()
                        .andExpect {
                            jsonPath("$.name") { value("수정된 사용자 조회") }
                        }
                }
            }
        }

        // ============================================
        // 권한 삭제
        // ============================================
        Given("DELETE /admin/permissions/{id} - 권한 삭제 API") {
            When("권한 삭제 요청") {
                justRun { permissionMappingService.deletePermission(4) }

                Then("204 No Content를 반환한다") {
                    mockMvc.delete("/admin/permissions/4") {}
                        .andExpect { status { isNoContent() } }

                    verify(exactly = 1) { permissionMappingService.deletePermission(4) }
                }
            }
        }
    }
}