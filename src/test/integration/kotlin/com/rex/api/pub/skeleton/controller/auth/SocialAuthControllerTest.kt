package com.rex.api.pub.skeleton.controller.auth

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.config.security.browser.BrowserSessionAuthenticator
import com.rex.api.pub.skeleton.dto.auth.SocialLoginRequestDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.mapping.SocialAuthMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.expectBadRequest
import com.rex.api.pub.skeleton.support.expectSuccess
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * SocialAuthController 통합 테스트.
 *
 * 소셜 로그인 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.auth.SocialAuthControllerTest"
 * ```
 *
 * 참고: 보안 필터 비활성화 (addFilters = false)하여 컨트롤러 로직만 테스트합니다.
 * 인증이 필요한 엔드포인트 (link, unlink, accounts)는 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [SocialAuthController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class SocialAuthControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var socialAuthMappingService: SocialAuthMappingService

    @MockkBean
    lateinit var clientIpResolver: ClientIpResolver

    @MockkBean
    lateinit var browserSessionAuthenticator: BrowserSessionAuthenticator

    init {
        // ============================================
        // UC-SOCIAL-001: 소셜 로그인 URL 조회 (인증 불필요)
        // ============================================
        Given("GET /auth/oauth2/{provider}/authorize - 소셜 로그인 시작") {
            When("Google Provider로 요청") {
                val expectedUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=cid&state=issued-state"
                every { socialAuthMappingService.issueAuthorizationRedirect(SocialProvider.GOOGLE, any()) } returns expectedUrl

                Then("브라우저 세션 ID 로 state 를 발급받아 provider 인가 URL 로 302 한다") {
                    val result =
                        mockMvc.get("/auth/oauth2/GOOGLE/authorize").andExpect {
                            status { isFound() }
                            header { string("Location", expectedUrl) }
                        }.andReturn()

                    val sessionId = result.request.getSession(false)?.id.shouldNotBeNull()
                    verify(exactly = 1) { socialAuthMappingService.issueAuthorizationRedirect(SocialProvider.GOOGLE, sessionId) }
                }
            }

            When("지원하지 않는 Provider로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.get("/auth/oauth2/FACEBOOK/authorize") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isBadRequest() }
                    }
                }
            }
        }

        Given("GET /auth/oauth2/{provider}/callback - state 검증") {
            When("state 가 이 브라우저 세션에서 발급한 값이 아니면") {
                every { socialAuthMappingService.consumeState(SocialProvider.GOOGLE, any(), "forged") } returns false

                Then("코드 교환 없이 invalid_state 로 302 한다") {
                    mockMvc.get("/auth/oauth2/google/callback") {
                        param("code", "provider-code")
                        param("state", "forged")
                    }.andExpect {
                        status { isFound() }
                        header { string("Location", org.hamcrest.Matchers.startsWith("/demo/auth/callback?error=invalid_state")) }
                    }

                    verify(exactly = 0) { socialAuthMappingService.socialLogin(any(), any(), any(), any(), any()) }
                }
            }
        }

        // ============================================
        // UC-SOCIAL-003: 소셜 로그인 (코드 교환) - 인증 불필요
        // ============================================
        Given("GET /auth/oauth2/{provider}/callback - provider 오류 콜백") {
            When("state 가 맞고 error·error_description 에 &·#·공백이 섞여 오면") {
                every { socialAuthMappingService.consumeState(SocialProvider.GOOGLE, any(), "issued") } returns true

                Then("데모 콜백으로 302 하되 값 하나에 인코딩 한 번 — 구분자로 새지 않는다") {
                    mockMvc.get("/auth/oauth2/google/callback") {
                        param("state", "issued")
                        param("error", "access denied")
                        param("error_description", "a&b#c d")
                    }.andExpect {
                        status { isFound() }
                        header { string("Location", "/demo/auth/callback?error=access+denied&error_description=a%26b%23c+d") }
                    }
                }
            }
        }

        Given("POST /auth/oauth2/{provider}/login - 소셜 로그인 API") {
            val loginRequest = SocialLoginRequestDto(
                code = "valid-authorization-code",
                deviceType = "WEB",
                deviceInfo = "Chrome Browser",
            )
            val expectedTokens = TokenResponseModel(
                accessToken = "social-access-token",
                refreshToken = "social-refresh-token",
                tokenType = "Bearer",
                expiresIn = 3600,
            )

            When("유효한 코드로 Google 로그인 요청") {
                every { clientIpResolver.resolve(any()) } returns "203.0.113.10"
                every {
                    socialAuthMappingService.socialLogin(
                        provider = SocialProvider.GOOGLE,
                        code = any(),
                        deviceType = any(),
                        deviceInfo = any(),
                        ipAddress = "203.0.113.10",
                    )
                } returns expectedTokens

                Then("200 OK와 토큰을 반환한다") {
                    mockMvc.postJson("/auth/oauth2/GOOGLE/login", loginRequest)
                        .expectSuccess()
                        .andExpect {
                            jsonPath("$.accessToken") { value("social-access-token") }
                            jsonPath("$.refreshToken") { value("social-refresh-token") }
                            jsonPath("$.tokenType") { value("Bearer") }
                        }

                    verify(exactly = 1) {
                        socialAuthMappingService.socialLogin(
                            provider = SocialProvider.GOOGLE,
                            code = any(),
                            deviceType = any(),
                            deviceInfo = any(),
                            ipAddress = "203.0.113.10",
                        )
                    }
                }
            }

            When("빈 코드로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/oauth2/GOOGLE/login",
                        mapOf(
                            "code" to "",
                        ),
                    )
                        .expectBadRequest()
                }
            }
        }

        // ============================================
        // 인증이 필요한 엔드포인트 - E2E 테스트에서 검증
        // ============================================
        // UC-SOCIAL-004: POST /auth/oauth2/{provider}/link - 소셜 계정 연동
        // UC-SOCIAL-005: DELETE /auth/oauth2/{provider}/unlink - 소셜 계정 연동 해제
        // UC-SOCIAL-006: GET /auth/oauth2/accounts - 연동된 소셜 계정 목록 조회
        // 위 엔드포인트들은 @AuthenticationPrincipal을 사용하므로 E2E 테스트에서 검증합니다.
    }
}