package com.rex.api.pub.skeleton.controller.auth

import com.ninjasquad.springmockk.MockkBean
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

    init {
        // ============================================
        // UC-SOCIAL-001: 소셜 로그인 URL 조회 (인증 불필요)
        // ============================================
        Given("GET /auth/oauth2/{provider}/authorize - 소셜 로그인 URL 조회 API") {
            When("Google Provider로 요청") {
                val expectedUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=..."
                every { socialAuthMappingService.getAuthorizationUrl(SocialProvider.GOOGLE, any()) } returns expectedUrl

                Then("200 OK와 인증 URL을 반환한다") {
                    mockMvc.get("/auth/oauth2/GOOGLE/authorize") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.authorizationUrl") { exists() }
                        jsonPath("$.state") { exists() }
                    }

                    verify(exactly = 1) { socialAuthMappingService.getAuthorizationUrl(SocialProvider.GOOGLE, any()) }
                }
            }

            When("Naver Provider로 요청") {
                val expectedUrl = "https://nid.naver.com/oauth2.0/authorize?client_id=..."
                every { socialAuthMappingService.getAuthorizationUrl(SocialProvider.NAVER, any()) } returns expectedUrl

                Then("200 OK와 인증 URL을 반환한다") {
                    mockMvc.get("/auth/oauth2/NAVER/authorize") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.authorizationUrl") { exists() }
                        jsonPath("$.state") { exists() }
                    }
                }
            }

            When("Kakao Provider로 요청") {
                val expectedUrl = "https://kauth.kakao.com/oauth/authorize?client_id=..."
                every { socialAuthMappingService.getAuthorizationUrl(SocialProvider.KAKAO, any()) } returns expectedUrl

                Then("200 OK와 인증 URL을 반환한다") {
                    mockMvc.get("/auth/oauth2/KAKAO/authorize") {
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.authorizationUrl") { exists() }
                        jsonPath("$.state") { exists() }
                    }
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

        // ============================================
        // UC-SOCIAL-003: 소셜 로그인 (코드 교환) - 인증 불필요
        // ============================================
        Given("GET /auth/oauth2/{provider}/callback - provider 오류 콜백") {
            When("error·error_description 에 &·#·공백이 섞여 오면") {
                Then("데모 콜백으로 302 하되 값 하나에 인코딩 한 번 — 구분자로 새지 않는다") {
                    mockMvc.get("/auth/oauth2/google/callback") {
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