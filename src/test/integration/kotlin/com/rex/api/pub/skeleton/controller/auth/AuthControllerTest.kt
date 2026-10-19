package com.rex.api.pub.skeleton.controller.auth

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.fixture.AuthFixture
import com.rex.api.pub.skeleton.model.auth.AccessTokenResponseModel
import com.rex.api.pub.skeleton.model.auth.LoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.TestJwtTokenGenerator
import com.rex.api.pub.skeleton.support.expectBadRequest
import com.rex.api.pub.skeleton.support.expectCreated
import com.rex.api.pub.skeleton.support.expectSuccess
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc

/**
 * AuthController 통합 테스트.
 *
 * MockMvc를 사용하여 Auth API 엔드포인트를 테스트합니다.
 * AuthService는 MockkBean으로 모킹하여 컨트롤러 레이어만 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.auth.AuthControllerTest"
 * ```
 *
 * 참고: 보안 필터 비활성화 (addFilters = false)하여 컨트롤러 로직만 테스트합니다.
 * 인증/인가 통합 테스트는 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [AuthController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AuthControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var authMappingService: AuthMappingService

    @MockkBean
    lateinit var clientIpResolver: ClientIpResolver

    private val tokenGenerator = TestJwtTokenGenerator.DEFAULT

    init {
        // ============================================
        // UC-AUTH-001: 회원가입
        // ============================================
        Given("POST /auth/signup - 회원가입 API") {
            val signupRequest = AuthFixture.signupRequest(
                loginId = "newuser",
                password = "Test@1234!",
                email = "new@example.com",
            )
            val expectedUserInfo = AuthFixture.authUserInfoModel(
                id = 1L,
                loginId = "newuser",
                email = "new@example.com",
            )

            When("유효한 데이터로 회원가입 요청") {
                every { authMappingService.signup(any()) } returns expectedUserInfo

                Then("201 Created와 UserInfoDto를 반환한다") {
                    mockMvc.postJson("/auth/signup", signupRequest)
                        .expectCreated()
                        .andExpect {
                            jsonPath("$.id") { value(1) }
                            jsonPath("$.loginId") { value("newuser") }
                            jsonPath("$.email") { value("new@example.com") }
                            jsonPath("$.groups[0]") { value("USER") }
                        }

                    verify(exactly = 1) { authMappingService.signup(any()) }
                }
            }

            When("빈 로그인 ID로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/signup",
                        mapOf(
                            "loginId" to "",
                            "password" to "Test@1234!",
                        ),
                    )
                        .expectBadRequest()
                }
            }

            When("짧은 로그인 ID로 요청 (4자 미만)") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/signup",
                        mapOf(
                            "loginId" to "abc",
                            "password" to "Test@1234!",
                        ),
                    )
                        .expectBadRequest()
                }
            }

            When("짧은 비밀번호로 요청 (8자 미만)") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/signup",
                        mapOf(
                            "loginId" to "validuser",
                            "password" to "1234567",
                        ),
                    )
                        .expectBadRequest()
                }
            }

            When("잘못된 이메일 형식으로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/signup",
                        mapOf(
                            "loginId" to "validuser",
                            "password" to "Test@1234!",
                            "email" to "invalid-email",
                        ),
                    )
                        .expectBadRequest()
                }
            }
        }

        // ============================================
        // UC-AUTH-002: 로그인
        // ============================================
        Given("POST /auth/login - 로그인 API") {
            val loginRequest = AuthFixture.loginRequest()
            val expectedTokenResponse = TokenResponseModel(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token",
                tokenType = "Bearer",
                expiresIn = 3600,
            )

            When("유효한 자격 증명으로 로그인 요청") {
                every { clientIpResolver.resolve(any()) } returns "203.0.113.10"
                every { authMappingService.login(any(), "203.0.113.10") } returns LoginOutcomeModel.Tokens(expectedTokenResponse)

                Then("200 OK와 TokenResponseDto를 반환한다") {
                    mockMvc.postJson("/auth/login", loginRequest)
                        .expectSuccess()
                        .andExpect {
                            jsonPath("$.accessToken") { value("test-access-token") }
                            jsonPath("$.refreshToken") { value("test-refresh-token") }
                            jsonPath("$.tokenType") { value("Bearer") }
                            jsonPath("$.expiresIn") { value(3600) }
                        }

                    // 클라이언트 IP 는 ClientIpResolver(신뢰 프록시 검사)가 정한 값 그대로 BizService 로 간다 — 헤더를 직접 읽지 않는다
                    verify(exactly = 1) { authMappingService.login(any(), "203.0.113.10") }
                }
            }

            When("빈 로그인 ID로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/login",
                        mapOf(
                            "loginId" to "",
                            "password" to "Test@1234!",
                        ),
                    )
                        .expectBadRequest()
                }
            }

            When("빈 비밀번호로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson(
                        "/auth/login",
                        mapOf(
                            "loginId" to "testuser",
                            "password" to "",
                        ),
                    )
                        .expectBadRequest()
                }
            }
        }

        // ============================================
        // UC-AUTH-003: 로그아웃
        // 참고: 인증이 필요한 엔드포인트 - E2E 테스트에서 전체 흐름 테스트
        // @WebMvcTest에서는 @AuthenticationPrincipal이 null이 되어 테스트 불가
        // ============================================

        // ============================================
        // UC-AUTH-004: 토큰 갱신
        // ============================================
        Given("POST /auth/refresh - 토큰 갱신 API") {
            val refreshToken = tokenGenerator.generateRefreshToken(userId = 1L, sessionId = "test-session")
            val expectedResponse = AccessTokenResponseModel(
                accessToken = "new-access-token",
                tokenType = "Bearer",
                expiresIn = 3600,
            )

            When("유효한 리프레시 토큰으로 요청") {
                every { authMappingService.refresh(any()) } returns expectedResponse

                Then("200 OK와 새 액세스 토큰을 반환한다") {
                    mockMvc.postJson("/auth/refresh", AuthFixture.refreshTokenRequest(refreshToken))
                        .expectSuccess()
                        .andExpect {
                            jsonPath("$.accessToken") { value("new-access-token") }
                            jsonPath("$.tokenType") { value("Bearer") }
                            jsonPath("$.expiresIn") { value(3600) }
                        }

                    verify(exactly = 1) { authMappingService.refresh(any()) }
                }
            }

            When("빈 리프레시 토큰으로 요청") {
                Then("400 Bad Request를 반환한다") {
                    mockMvc.postJson("/auth/refresh", mapOf("refreshToken" to ""))
                        .expectBadRequest()
                }
            }
        }

        // ============================================
        // UC-AUTH-005: 비밀번호 변경
        // 참고: 인증이 필요한 엔드포인트 - E2E 테스트에서 전체 흐름 테스트
        // @WebMvcTest에서는 @AuthenticationPrincipal이 null이 되어 테스트 불가
        // 입력 검증 테스트만 수행 가능 (인증 우회 시)
        // ============================================
    }
}