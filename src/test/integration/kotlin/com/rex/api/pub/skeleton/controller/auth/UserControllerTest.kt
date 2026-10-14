package com.rex.api.pub.skeleton.controller.auth

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import com.rex.api.pub.skeleton.service.mapping.UserSelfMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete

/**
 * UserController 통합 테스트.
 *
 * 사용자 관련 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.auth.UserControllerTest"
 * ```
 *
 * 참고: UserController의 모든 엔드포인트는 @AuthenticationPrincipal을 사용하므로
 * @WebMvcTest에서는 테스트가 불가능합니다.
 * 전체 인증 흐름 테스트는 E2E 테스트에서 수행합니다.
 *
 * E2E 테스트 대상:
 * - UC-AUTH-006: GET /users/me - 내 정보 조회
 * - UC-AUTH-007: DELETE /users/me/sessions - 전체 세션 로그아웃
 * - UC-AUTH-008: DELETE /users/me/sessions/{sessionId} - 특정 세션 로그아웃 (소유권 — 타인 403 · 본인 204)
 */
@WebMvcTest(controllers = [UserController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class UserControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var authMappingService: AuthMappingService

    @MockkBean
    lateinit var userSelfMappingService: UserSelfMappingService

    init {
        // 인증 필터가 꺼진 슬라이스(addFilters = false)라 SecurityContextHolderFilter 가 없다 — MockMvc 는 호출 스레드에서 돌므로
        // 홀더에 principal 을 직접 넣는다(OAuth2AuthorizationControllerTest 와 같은 방식). getMe·logoutAllSessions 는 E2E(AuthE2ETest)가 본다.
        Given("DELETE /users/me/sessions/{sessionId} — 요청자 1(세션 s-me)") {
            beforeTest {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(
                        AuthenticatedUser(userId = 1L, sessionId = "s-me", groups = emptyList(), permissions = emptyList()),
                        null,
                        emptyList(),
                    )
            }
            afterTest { SecurityContextHolder.clearContext() }

            When("매핑 서비스가 타인 세션이라 ForbiddenException 을 던지면") {
                every { userSelfMappingService.revokeSessionBySessionId(1L, "s-other") } throws
                    ForbiddenException("본인의 세션만 종료할 수 있습니다")

                Then("C-1 403 과 FORBIDDEN_ERROR(G008)") {
                    mockMvc.delete("/users/me/sessions/s-other")
                        .andExpect {
                            status { isForbidden() }
                            jsonPath("$.code") { value("FORBIDDEN_ERROR") }
                            jsonPath("$.divisionCode") { value("G008") }
                        }
                }
            }

            When("매핑 서비스가 정상 처리하면") {
                justRun { userSelfMappingService.revokeSessionBySessionId(1L, "s-other") }

                Then("C-2 204 이고 요청자 id 는 principal 에서 온다") {
                    mockMvc.delete("/users/me/sessions/s-other")
                        .andExpect { status { isNoContent() } }
                    verify(exactly = 1) { userSelfMappingService.revokeSessionBySessionId(1L, "s-other") }
                }
            }
        }
    }
}