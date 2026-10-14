package com.rex.api.pub.skeleton.controller.user

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.dto.user.EmailVerificationConfirmDto
import com.rex.api.pub.skeleton.dto.user.EmailVerificationRequestDto
import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetConfirmDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetRequestDto
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.model.user.UserProfileModel
import com.rex.api.pub.skeleton.model.user.UserSessionModel
import com.rex.api.pub.skeleton.service.mapping.UserSelfMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.putJson
import com.rex.api.pub.skeleton.util.ClientIpResolver
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
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * UserSelfServiceController 통합 테스트.
 *
 * 사용자 셀프 서비스 API 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.user.UserSelfServiceControllerTest"
 * ```
 *
 * 참고: @AuthenticationPrincipal을 사용하는 엔드포인트는 보안 필터 비활성화 후
 * 컨트롤러 로직만 테스트합니다. 전체 인증 흐름은 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [UserSelfServiceController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class UserSelfServiceControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var mappingService: UserSelfMappingService

    @MockkBean
    lateinit var clientIpResolver: ClientIpResolver

    init {
        // ============================================
        // 비밀번호 재설정 요청 (인증 불필요)
        // ============================================
        Given("POST /user/password/reset-request - 비밀번호 재설정 요청") {
            When("유효한 이메일로 요청") {
                val request = PasswordResetRequestDto(email = "user@example.com")

                justRun { mappingService.requestPasswordReset(any()) }

                Then("200 OK와 메시지 반환") {
                    mockMvc.postJson("/user/password/reset-request", request)
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.message") { exists() }
                        }

                    verify(exactly = 1) { mappingService.requestPasswordReset("user@example.com") }
                }
            }
        }

        // ============================================
        // 비밀번호 재설정 완료 (인증 불필요)
        // ============================================
        Given("POST /user/password/reset - 비밀번호 재설정 완료") {
            When("유효한 토큰으로 비밀번호 재설정") {
                val request = PasswordResetConfirmDto(
                    token = "valid-reset-token",
                    newPassword = "NewPassword@123",
                )

                every { mappingService.resetPassword(any(), any()) } returns true

                Then("200 OK와 성공 결과 반환") {
                    mockMvc.postJson("/user/password/reset", request)
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.success") { value(true) }
                        }
                }
            }

            When("유효하지 않은 토큰으로 비밀번호 재설정") {
                val request = PasswordResetConfirmDto(
                    token = "invalid-token",
                    newPassword = "NewPassword@123",
                )

                every { mappingService.resetPassword(any(), any()) } returns false

                Then("400 Bad Request와 실패 결과 반환") {
                    mockMvc.postJson("/user/password/reset", request)
                        .andExpect {
                            status { isBadRequest() }
                            jsonPath("$.success") { value(false) }
                        }
                }
            }
        }

        // ============================================
        // 이메일 인증 완료 (인증 불필요)
        // ============================================
        Given("POST /user/email/verify - 이메일 인증 완료") {
            When("유효한 토큰으로 이메일 인증") {
                val request = EmailVerificationConfirmDto(token = "valid-email-token")

                every { mappingService.verifyEmail(any()) } returns true

                Then("200 OK와 성공 결과 반환") {
                    mockMvc.postJson("/user/email/verify", request)
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.success") { value(true) }
                        }
                }
            }

            When("유효하지 않은 토큰으로 이메일 인증") {
                val request = EmailVerificationConfirmDto(token = "invalid-token")

                every { mappingService.verifyEmail(any()) } returns false

                Then("400 Bad Request와 실패 결과 반환") {
                    mockMvc.postJson("/user/email/verify", request)
                        .andExpect {
                            status { isBadRequest() }
                            jsonPath("$.success") { value(false) }
                        }
                }
            }
        }
    }
}