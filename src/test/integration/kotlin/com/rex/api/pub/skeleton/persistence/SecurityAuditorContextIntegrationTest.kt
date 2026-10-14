package com.rex.api.pub.skeleton.persistence

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.config.security.SecurityConfiguration
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.support.AuditProbeConfiguration
import com.rex.api.pub.skeleton.support.TestJwtTokenGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * 감사 주체가 **인증 결과에서** 나오는지, **실제 `SecurityFilterChain`** 을 지나 확인한다.
 *
 * ## `test` 프로파일을 켜지 않는다
 *
 * 통합테스트 기본 프로파일(`test`)에서는 `TestSecurityConfiguration` 이 모든 요청을
 * permitAll 하고 JWT 필터를 넣지 않으며, 진짜 `SecurityConfiguration` 은
 * `@Profile("!test")` 라 **아예 뜨지 않는다.** 그 상태로 검증하면 인증이 없는 경로를
 * 보고 통과한다 — 그래서 `testcontainers` 만 켠다.
 *
 * ## 왜 MockMvc 인가
 *
 * 필터를 직접 순서대로 호출하면 **`addFilterAfter` 등록이 빠지거나 순서가 틀려도
 * 통과한다.** 실제 체인을 지나야 등록·순서까지 함께 검증된다.
 * 저장 지점은 permitAll 경로의 테스트 전용 프로브다([AuditProbeConfiguration]).
 *
 * 비인증 경로(헤더 폴백·기본 불신)는 `AuditorChainIntegrationTest` ·
 * `AuditorHeaderDistrustIntegrationTest` 가 같은 프로브로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
@Import(AuditProbeConfiguration::class)
class SecurityAuditorContextIntegrationTest(
    private val mockMvc: MockMvc,
    private val applicationContext: ApplicationContext,
) : BehaviorSpec() {
    @MockkBean
    lateinit var authBizService: AuthBizService

    init {
        Given("통합테스트 컨텍스트") {
            Then("N-0 진짜 SecurityConfiguration 이 떠 있다") {
                // 이것이 없으면 아래가 인증 없는 경로를 보고 통과한다.
                applicationContext.getBeanNamesForType(SecurityConfiguration::class.java).size shouldBe 1
            }

            Then("N-0b 감사 필터의 서블릿 자동 등록이 꺼져 있다") {
                // 켜져 있으면 인증 전에 한 번 더 돌아 헤더 폴백이 먼저 기록된다.
                val registration =
                    applicationContext.getBean(
                        "securityAuditorContextFilterRegistration",
                        FilterRegistrationBean::class.java,
                    )

                registration.isEnabled shouldBe false
            }
        }

        Given("JWT 로 인증된 요청이 실제 체인을 지날 때") {
            Then("N-3 감사 주체가 인증 주체의 userId 가 된다") {
                stubSession(42L)

                mockMvc
                    .perform(post(AuditProbeConfiguration.PATH).header("Authorization", "Bearer ${tokenFor(42L)}"))
                    .andExpect(status().isOk)
                    .andExpect(content().string(AuditProbeConfiguration.body(observed = 42, createdBy = 42)))
            }

            // 헤더를 직접 넣을 수 있는 호출자가 감사를 위조하지 못해야 한다.
            Then("N-4 헤더가 달라도 인증이 이긴다") {
                stubSession(42L)

                mockMvc
                    .perform(
                        post(AuditProbeConfiguration.PATH)
                            .header("Authorization", "Bearer ${tokenFor(42L)}")
                            .header(Constant.HEADER_USER_ID, "99"),
                    ).andExpect(status().isOk)
                    .andExpect(content().string(AuditProbeConfiguration.body(observed = 42, createdBy = 42)))
            }
        }
    }

    /** 세션 저장소가 아니라 **인증 결과가 감사 주체로 이어지는가**를 보는 스펙이다. */
    private fun stubSession(userId: Long) {
        every { authBizService.validateSession(SESSION_ID) } returns
            SessionInfo(
                userId = userId,
                sessionId = SESSION_ID,
                groups = listOf("USER"),
                permissions = emptyList(),
                deviceType = null,
                ipAddress = null,
                issuedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(3600),
            )
    }

    private fun tokenFor(userId: Long): String =
        TestJwtTokenGenerator.DEFAULT.generateUserToken(userId = userId, sessionId = SESSION_ID)

    companion object {
        private const val SESSION_ID = "audit-probe-session"
    }
}