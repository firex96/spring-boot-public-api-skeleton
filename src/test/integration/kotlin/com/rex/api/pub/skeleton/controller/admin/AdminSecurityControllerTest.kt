package com.rex.api.pub.skeleton.controller.admin

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.dto.security.UnlockAccountRequestDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.model.security.BlockedIpModel
import com.rex.api.pub.skeleton.model.security.IpBlockStatusModel
import com.rex.api.pub.skeleton.model.security.LoginAttemptStatusModel
import com.rex.api.pub.skeleton.model.security.SecuritySettingsModel
import com.rex.api.pub.skeleton.service.mapping.SecurityMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.support.postJson
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

/**
 * AdminSecurityController 통합 테스트.
 *
 * 보안 관리 API (IP 차단, 계정 잠금) 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.admin.AdminSecurityControllerTest"
 * ```
 *
 * 참고: @AuthenticationPrincipal을 사용하는 엔드포인트(blockIp, unblockIp, unblockIpByAddress)는
 * 보안 필터 비활성화 후에는 테스트할 수 없으므로 E2E 테스트에서 수행합니다.
 */
@WebMvcTest(controllers = [AdminSecurityController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class AdminSecurityControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var mappingService: SecurityMappingService

    init {
        // ============================================
        // 차단된 IP 목록 조회
        // ============================================
        Given("GET /admin/security/blocked-ips - 차단된 IP 목록 조회") {
            When("기본 페이징으로 조회") {
                val now = LocalDateTime.now()
                val blockedIps = listOf(
                    BlockedIpModel(
                        id = 1L,
                        ipAddress = "192.168.1.100",
                        blockReason = BlockReason.BRUTE_FORCE,
                        description = "Brute force attack",
                        blockedAt = now,
                        expiresAt = now.plusHours(1),
                        blockedBy = 1L,
                        isActive = true,
                        unblockedAt = null,
                        unblockedBy = null,
                        isPermanent = false,
                    ),
                )
                val page = PageImpl(blockedIps, PageRequest.of(0, 20), 1)

                every { mappingService.getBlockedIps(any()) } returns page

                Then("200 OK와 페이징된 차단 IP 목록 반환") {
                    mockMvc.get("/admin/security/blocked-ips")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.content") { isArray() }
                            jsonPath("$.content[0].ipAddress") { value("192.168.1.100") }
                        }
                }
            }
        }

        // ============================================
        // IP 차단 상태 확인
        // ============================================
        Given("GET /admin/security/blocked-ips/check/{ipAddress} - IP 차단 상태 확인") {
            When("차단된 IP 확인") {
                val now = LocalDateTime.now()
                val blockedIpModel = BlockedIpModel(
                    id = 1L,
                    ipAddress = "192.168.1.100",
                    blockReason = BlockReason.BRUTE_FORCE,
                    description = "Brute force attack",
                    blockedAt = now,
                    expiresAt = now.plusHours(1),
                    blockedBy = 1L,
                    isActive = true,
                    unblockedAt = null,
                    unblockedBy = null,
                    isPermanent = false,
                )
                val status = IpBlockStatusModel(
                    ipAddress = "192.168.1.100",
                    isBlocked = true,
                    blockInfo = blockedIpModel,
                )

                every { mappingService.checkIpBlocked("192.168.1.100") } returns status

                Then("200 OK와 차단 상태 정보 반환") {
                    mockMvc.get("/admin/security/blocked-ips/check/192.168.1.100")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.ipAddress") { value("192.168.1.100") }
                            jsonPath("$.isBlocked") { value(true) }
                        }
                }
            }

            When("차단되지 않은 IP 확인") {
                val status = IpBlockStatusModel(
                    ipAddress = "10.0.0.1",
                    isBlocked = false,
                    blockInfo = null,
                )

                every { mappingService.checkIpBlocked("10.0.0.1") } returns status

                Then("200 OK와 미차단 상태 정보 반환") {
                    mockMvc.get("/admin/security/blocked-ips/check/10.0.0.1")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.isBlocked") { value(false) }
                        }
                }
            }
        }

        // ============================================
        // 계정 잠금 해제
        // ============================================
        Given("POST /admin/security/accounts/unlock - 계정 잠금 해제") {
            When("유효한 사용자명으로 잠금 해제") {
                val request = UnlockAccountRequestDto(username = "lockeduser")

                justRun { mappingService.unlockAccount("lockeduser") }

                Then("200 OK와 메시지 반환") {
                    mockMvc.postJson("/admin/security/accounts/unlock", request)
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.message") { exists() }
                        }

                    verify(exactly = 1) { mappingService.unlockAccount("lockeduser") }
                }
            }
        }

        // ============================================
        // 계정 로그인 시도 상태 확인
        // ============================================
        Given("GET /admin/security/accounts/status/{username} - 계정 상태 확인") {
            When("잠긴 계정 상태 확인") {
                val status = LoginAttemptStatusModel(
                    username = "lockeduser",
                    currentAttempts = 5,
                    maxAttempts = 5,
                    isLocked = true,
                    remainingLockTimeSeconds = 1800L,
                )

                every { mappingService.getAccountStatus("lockeduser") } returns status

                Then("200 OK와 계정 상태 정보 반환") {
                    mockMvc.get("/admin/security/accounts/status/lockeduser")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.username") { value("lockeduser") }
                            jsonPath("$.isLocked") { value(true) }
                            jsonPath("$.currentAttempts") { value(5) }
                        }
                }
            }
        }

        // ============================================
        // 보안 설정 조회
        // ============================================
        Given("GET /admin/security/settings - 보안 설정 조회") {
            When("현재 보안 설정 조회") {
                val settings = SecuritySettingsModel(
                    maxLoginAttempts = 5,
                    loginAttemptWindowMinutes = 15,
                    accountLockDurationMinutes = 30,
                    maxIpLoginAttempts = 10,
                    ipBlockDurationMinutes = 60,
                    permanentBlockThreshold = 50,
                    enableBruteForceProtection = true,
                    enableIpBlocking = true,
                )

                every { mappingService.getSecuritySettings() } returns settings

                Then("200 OK와 보안 설정 정보 반환") {
                    mockMvc.get("/admin/security/settings")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.maxLoginAttempts") { value(5) }
                            jsonPath("$.enableBruteForceProtection") { value(true) }
                        }
                }
            }
        }

        // ============================================
        // @AuthenticationPrincipal이 필요한 엔드포인트
        // ============================================
        // 참고: 아래 엔드포인트들은 @AuthenticationPrincipal을 사용하여
        // 보안 필터가 비활성화된 @WebMvcTest에서는 테스트할 수 없습니다.
        // E2E 테스트에서 전체 인증 흐름과 함께 테스트합니다.
        //
        // - POST /admin/security/blocked-ips (IP 차단)
        // - DELETE /admin/security/blocked-ips/{id} (IP 차단 해제)
        // - DELETE /admin/security/blocked-ips/ip/{ipAddress} (IP 주소로 차단 해제)
    }
}