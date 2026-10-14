package com.rex.api.pub.skeleton.controller.admin

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.AuditResult
import com.rex.api.pub.skeleton.model.audit.AuditLogModel
import com.rex.api.pub.skeleton.service.mapping.AuditMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

/**
 * AdminAuditController 통합 테스트.
 *
 * 감사 로그 관리 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.admin.AdminAuditControllerTest"
 * ```
 */
@WebMvcTest(controllers = [AdminAuditController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AdminAuditControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var mappingService: AuditMappingService

    init {
        // ============================================
        // 감사 로그 목록 조회
        // ============================================
        Given("GET /admin/audit/logs - 감사 로그 목록 조회") {
            When("기본 페이징으로 조회") {
                val now = LocalDateTime.now()
                val logs = listOf(
                    AuditLogModel(
                        id = 1L,
                        userId = 1L,
                        username = "testuser",
                        eventType = AuditEventType.LOGIN_SUCCESS,
                        eventCategory = "AUTH",
                        result = AuditResult.SUCCESS,
                        ipAddress = "127.0.0.1",
                        userAgent = "Mozilla/5.0",
                        resourceType = null,
                        resourceId = null,
                        action = null,
                        description = null,
                        oldValue = null,
                        newValue = null,
                        failureReason = null,
                        sessionId = null,
                        clientId = null,
                        createdAt = now,
                    ),
                )
                val page = PageImpl(logs, PageRequest.of(0, 20), 1)

                every { mappingService.getAuditLogs(any()) } returns page

                Then("200 OK와 페이징된 로그 목록 반환") {
                    mockMvc.get("/admin/audit/logs")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.content") { isArray() }
                            jsonPath("$.content[0].id") { value(1) }
                            jsonPath("$.content[0].eventType") { value("LOGIN_SUCCESS") }
                        }
                }
            }
        }

        // ============================================
        // 감사 로그 검색
        // ============================================
        Given("GET /admin/audit/logs/search - 감사 로그 검색") {
            When("userId로 검색") {
                val now = LocalDateTime.now()
                val logs = listOf(
                    AuditLogModel(
                        id = 1L,
                        userId = 100L,
                        username = "searchuser",
                        eventType = AuditEventType.LOGIN_SUCCESS,
                        eventCategory = "AUTH",
                        result = AuditResult.SUCCESS,
                        ipAddress = "192.168.1.1",
                        userAgent = "Chrome",
                        resourceType = null,
                        resourceId = null,
                        action = null,
                        description = null,
                        oldValue = null,
                        newValue = null,
                        failureReason = null,
                        sessionId = null,
                        clientId = null,
                        createdAt = now,
                    ),
                )
                val page = PageImpl(logs, PageRequest.of(0, 20), 1)

                every {
                    mappingService.searchAuditLogs(
                        userId = 100L,
                        eventType = null,
                        eventCategory = null,
                        result = null,
                        startDate = null,
                        endDate = null,
                        pageable = any(),
                    )
                } returns page

                Then("200 OK와 검색 결과 반환") {
                    mockMvc.get("/admin/audit/logs/search") {
                        param("userId", "100")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.content[0].userId") { value(100) }
                    }
                }
            }

            When("eventType으로 검색") {
                val now = LocalDateTime.now()
                val logs = listOf(
                    AuditLogModel(
                        id = 2L,
                        userId = 1L,
                        username = "testuser",
                        eventType = AuditEventType.LOGOUT,
                        eventCategory = "AUTH",
                        result = AuditResult.SUCCESS,
                        ipAddress = "127.0.0.1",
                        userAgent = "Firefox",
                        resourceType = null,
                        resourceId = null,
                        action = null,
                        description = null,
                        oldValue = null,
                        newValue = null,
                        failureReason = null,
                        sessionId = null,
                        clientId = null,
                        createdAt = now,
                    ),
                )
                val page = PageImpl(logs, PageRequest.of(0, 20), 1)

                every {
                    mappingService.searchAuditLogs(
                        userId = null,
                        eventType = AuditEventType.LOGOUT,
                        eventCategory = null,
                        result = null,
                        startDate = null,
                        endDate = null,
                        pageable = any(),
                    )
                } returns page

                Then("200 OK와 검색 결과 반환") {
                    mockMvc.get("/admin/audit/logs/search") {
                        param("eventType", "LOGOUT")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.content[0].eventType") { value("LOGOUT") }
                    }
                }
            }
        }

        // ============================================
        // 사용자별 감사 로그 조회
        // ============================================
        Given("GET /admin/audit/logs/user/{userId} - 사용자별 감사 로그 조회") {
            When("특정 사용자의 로그 조회") {
                val now = LocalDateTime.now()
                val logs = listOf(
                    AuditLogModel(
                        id = 1L,
                        userId = 5L,
                        username = "user5",
                        eventType = AuditEventType.PASSWORD_CHANGE,
                        eventCategory = "AUTH",
                        result = AuditResult.SUCCESS,
                        ipAddress = "10.0.0.1",
                        userAgent = "Safari",
                        resourceType = null,
                        resourceId = null,
                        action = null,
                        description = null,
                        oldValue = null,
                        newValue = null,
                        failureReason = null,
                        sessionId = null,
                        clientId = null,
                        createdAt = now,
                    ),
                )
                val page = PageImpl(logs, PageRequest.of(0, 20), 1)

                every { mappingService.getAuditLogsByUser(5L, any()) } returns page

                Then("200 OK와 해당 사용자의 로그 반환") {
                    mockMvc.get("/admin/audit/logs/user/5")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.content[0].userId") { value(5) }
                        }
                }
            }
        }

        // ============================================
        // 이벤트 타입별 감사 로그 조회
        // ============================================
        Given("GET /admin/audit/logs/event/{eventType} - 이벤트 타입별 감사 로그 조회") {
            When("LOGIN_SUCCESS 이벤트 로그 조회") {
                val now = LocalDateTime.now()
                val logs = listOf(
                    AuditLogModel(
                        id = 1L,
                        userId = 1L,
                        username = "loginuser",
                        eventType = AuditEventType.LOGIN_SUCCESS,
                        eventCategory = "AUTH",
                        result = AuditResult.SUCCESS,
                        ipAddress = "127.0.0.1",
                        userAgent = "Chrome",
                        resourceType = null,
                        resourceId = null,
                        action = null,
                        description = null,
                        oldValue = null,
                        newValue = null,
                        failureReason = null,
                        sessionId = null,
                        clientId = null,
                        createdAt = now,
                    ),
                )
                val page = PageImpl(logs, PageRequest.of(0, 20), 1)

                every { mappingService.getAuditLogsByEventType(AuditEventType.LOGIN_SUCCESS, any()) } returns page

                Then("200 OK와 해당 이벤트 타입 로그 반환") {
                    mockMvc.get("/admin/audit/logs/event/LOGIN_SUCCESS")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.content[0].eventType") { value("LOGIN_SUCCESS") }
                        }
                }
            }
        }

        // ============================================
        // 이벤트 타입 목록 조회
        // ============================================
        Given("GET /admin/audit/event-types - 이벤트 타입 목록") {
            When("이벤트 타입 목록 조회") {
                val eventTypes = listOf("LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGOUT", "PASSWORD_CHANGE")

                every { mappingService.getEventTypes() } returns eventTypes

                Then("200 OK와 이벤트 타입 목록 반환") {
                    mockMvc.get("/admin/audit/event-types")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$") { isArray() }
                            jsonPath("$[0]") { value("LOGIN_SUCCESS") }
                        }
                }
            }
        }

        // ============================================
        // 이벤트 카테고리 목록 조회
        // ============================================
        Given("GET /admin/audit/event-categories - 이벤트 카테고리 목록") {
            When("이벤트 카테고리 목록 조회") {
                val categories = listOf("AUTH", "USER", "ADMIN", "OAUTH2")

                every { mappingService.getEventCategories() } returns categories

                Then("200 OK와 이벤트 카테고리 목록 반환") {
                    mockMvc.get("/admin/audit/event-categories")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$") { isArray() }
                            jsonPath("$[0]") { value("AUTH") }
                        }
                }
            }
        }
    }
}