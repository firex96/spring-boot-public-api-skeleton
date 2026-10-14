package com.rex.api.pub.skeleton.controller.admin

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.UpdateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientWithSecretModel
import com.rex.api.pub.skeleton.service.mapping.OAuth2ClientMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.putJson
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * AdminOAuth2ClientController 통합 테스트.
 *
 * OAuth2 클라이언트 관리 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.admin.AdminOAuth2ClientControllerTest"
 * ```
 */
@WebMvcTest(controllers = [AdminOAuth2ClientController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AdminOAuth2ClientControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var oAuth2ClientMappingService: OAuth2ClientMappingService

    init {
        val now = LocalDateTime.now()

        // ============================================
        // 전체 클라이언트 목록 조회
        // ============================================
        Given("GET /admin/oauth2/clients - 전체 클라이언트 목록 조회") {
            When("클라이언트 목록 조회") {
                val clients = listOf(
                    OAuth2ClientModel(
                        id = 1L,
                        clientId = "web-app",
                        clientName = "Web Application",
                        description = "Main web application",
                        redirectUris = listOf("https://example.com/callback"),
                        scopes = listOf("openid", "profile", "email"),
                        grantTypes = listOf("authorization_code", "refresh_token"),
                        isActive = true,
                        isFirstParty = true,
                        accessTokenValidity = 3600,
                        refreshTokenValidity = 604800,
                        requirePkce = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

                every { oAuth2ClientMappingService.getAllClients() } returns clients

                Then("200 OK와 클라이언트 목록 반환") {
                    mockMvc.get("/admin/oauth2/clients")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$") { isArray() }
                            jsonPath("$[0].clientId") { value("web-app") }
                            jsonPath("$[0].clientName") { value("Web Application") }
                        }
                }
            }
        }

        // ============================================
        // 활성 클라이언트 목록 조회
        // ============================================
        Given("GET /admin/oauth2/clients/active - 활성 클라이언트 목록 조회") {
            When("활성 클라이언트 목록 조회") {
                val activeClients = listOf(
                    OAuth2ClientModel(
                        id = 1L,
                        clientId = "active-app",
                        clientName = "Active Application",
                        description = "Active app",
                        redirectUris = listOf("https://active.com/callback"),
                        scopes = listOf("openid"),
                        grantTypes = listOf("authorization_code"),
                        isActive = true,
                        isFirstParty = false,
                        accessTokenValidity = 3600,
                        refreshTokenValidity = 604800,
                        requirePkce = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

                every { oAuth2ClientMappingService.getActiveClients() } returns activeClients

                Then("200 OK와 활성 클라이언트 목록 반환") {
                    mockMvc.get("/admin/oauth2/clients/active")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$[0].isActive") { value(true) }
                        }
                }
            }
        }

        // ============================================
        // 클라이언트 상세 조회
        // ============================================
        Given("GET /admin/oauth2/clients/{id} - 클라이언트 상세 조회") {
            When("존재하는 클라이언트 조회") {
                val client = OAuth2ClientModel(
                    id = 1L,
                    clientId = "web-app",
                    clientName = "Web Application",
                    description = "Main web application",
                    redirectUris = listOf("https://example.com/callback"),
                    scopes = listOf("openid", "profile", "email"),
                    grantTypes = listOf("authorization_code", "refresh_token"),
                    isActive = true,
                    isFirstParty = true,
                    accessTokenValidity = 3600,
                    refreshTokenValidity = 604800,
                    requirePkce = false,
                    createdAt = now,
                    updatedAt = now,
                )

                every { oAuth2ClientMappingService.getClient(1L) } returns client

                Then("200 OK와 클라이언트 정보 반환") {
                    mockMvc.get("/admin/oauth2/clients/1")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.id") { value(1) }
                            jsonPath("$.clientId") { value("web-app") }
                        }
                }
            }
        }

        // ============================================
        // 클라이언트 생성
        // ============================================
        Given("POST /admin/oauth2/clients - 클라이언트 생성") {
            When("유효한 데이터로 클라이언트 생성") {
                val request = CreateOAuth2ClientRequestDto(
                    clientName = "New Application",
                    description = "New app description",
                    redirectUris = listOf("https://newapp.com/callback"),
                    scopes = listOf("openid", "profile"),
                    grantTypes = listOf("authorization_code"),
                    isFirstParty = false,
                    accessTokenValidity = 3600,
                    refreshTokenValidity = 86400,
                )
                val createdClient = OAuth2ClientWithSecretModel(
                    id = 2L,
                    clientId = "new-app",
                    clientSecret = "generated-secret-12345",
                    clientName = "New Application",
                    description = "New app description",
                    redirectUris = listOf("https://newapp.com/callback"),
                    scopes = listOf("openid", "profile"),
                    grantTypes = listOf("authorization_code"),
                    isActive = true,
                    isFirstParty = false,
                    accessTokenValidity = 3600,
                    refreshTokenValidity = 86400,
                    requirePkce = false,
                    createdAt = now,
                    updatedAt = now,
                )

                every { oAuth2ClientMappingService.createClient(any()) } returns createdClient

                Then("201 Created와 시크릿 포함 클라이언트 정보 반환") {
                    mockMvc.postJson("/admin/oauth2/clients", request)
                        .andExpect {
                            status { isCreated() }
                            jsonPath("$.clientId") { value("new-app") }
                            jsonPath("$.clientSecret") { value("generated-secret-12345") }
                        }
                }
            }
        }

        // ============================================
        // 클라이언트 수정
        // ============================================
        Given("PUT /admin/oauth2/clients/{id} - 클라이언트 수정") {
            When("유효한 데이터로 클라이언트 수정") {
                val request = UpdateOAuth2ClientRequestDto(
                    clientName = "Updated Application",
                    description = "Updated description",
                    redirectUris = listOf("https://updated.com/callback"),
                    scopes = listOf("openid", "profile", "email"),
                    grantTypes = listOf("authorization_code", "refresh_token"),
                    isFirstParty = false,
                    accessTokenValidity = 7200,
                    refreshTokenValidity = 172800,
                )
                val updatedClient = OAuth2ClientModel(
                    id = 1L,
                    clientId = "web-app",
                    clientName = "Updated Application",
                    description = "Updated description",
                    redirectUris = listOf("https://updated.com/callback"),
                    scopes = listOf("openid", "profile", "email"),
                    grantTypes = listOf("authorization_code", "refresh_token"),
                    isActive = true,
                    isFirstParty = false,
                    accessTokenValidity = 7200,
                    refreshTokenValidity = 172800,
                    requirePkce = false,
                    createdAt = now,
                    updatedAt = now,
                )

                every { oAuth2ClientMappingService.updateClient(1L, any()) } returns updatedClient

                Then("200 OK와 수정된 클라이언트 정보 반환") {
                    mockMvc.putJson("/admin/oauth2/clients/1", request)
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.clientName") { value("Updated Application") }
                        }
                }
            }
        }

        // ============================================
        // 클라이언트 시크릿 재생성
        // ============================================
        Given("POST /admin/oauth2/clients/{id}/secret - 시크릿 재생성") {
            When("시크릿 재생성 요청") {
                val clientWithNewSecret = OAuth2ClientWithSecretModel(
                    id = 1L,
                    clientId = "web-app",
                    clientSecret = "new-regenerated-secret-67890",
                    clientName = "Web Application",
                    description = "Main web application",
                    redirectUris = listOf("https://example.com/callback"),
                    scopes = listOf("openid", "profile", "email"),
                    grantTypes = listOf("authorization_code", "refresh_token"),
                    isActive = true,
                    isFirstParty = true,
                    accessTokenValidity = 3600,
                    refreshTokenValidity = 604800,
                    requirePkce = false,
                    createdAt = now,
                    updatedAt = now,
                )

                every { oAuth2ClientMappingService.regenerateSecret(1L) } returns clientWithNewSecret

                Then("200 OK와 새 시크릿 포함 정보 반환") {
                    mockMvc.post("/admin/oauth2/clients/1/secret")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.clientSecret") { value("new-regenerated-secret-67890") }
                        }
                }
            }
        }

        // ============================================
        // 클라이언트 비활성화
        // ============================================
        Given("POST /admin/oauth2/clients/{id}/deactivate - 클라이언트 비활성화") {
            When("클라이언트 비활성화 요청") {
                val deactivatedClient = OAuth2ClientModel(
                    id = 1L,
                    clientId = "web-app",
                    clientName = "Web Application",
                    description = "Main web application",
                    redirectUris = listOf("https://example.com/callback"),
                    scopes = listOf("openid", "profile", "email"),
                    grantTypes = listOf("authorization_code", "refresh_token"),
                    isActive = false,
                    isFirstParty = true,
                    accessTokenValidity = 3600,
                    refreshTokenValidity = 604800,
                    requirePkce = false,
                    createdAt = now,
                    updatedAt = now,
                )

                every { oAuth2ClientMappingService.deactivateClient(1L) } returns deactivatedClient

                Then("200 OK와 비활성화된 클라이언트 정보 반환") {
                    mockMvc.post("/admin/oauth2/clients/1/deactivate")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.isActive") { value(false) }
                        }
                }
            }
        }

        // ============================================
        // 클라이언트 삭제
        // ============================================
        Given("DELETE /admin/oauth2/clients/{id} - 클라이언트 삭제") {
            When("클라이언트 삭제 요청") {
                justRun { oAuth2ClientMappingService.deleteClient(1L) }

                Then("204 No Content 반환") {
                    mockMvc.delete("/admin/oauth2/clients/1")
                        .andExpect {
                            status { isNoContent() }
                        }

                    verify(exactly = 1) { oAuth2ClientMappingService.deleteClient(1L) }
                }
            }
        }
    }
}