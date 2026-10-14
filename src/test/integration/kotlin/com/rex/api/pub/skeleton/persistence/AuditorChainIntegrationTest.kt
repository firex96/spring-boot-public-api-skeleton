package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import com.rex.api.pub.skeleton.support.AuditProbeConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 사용자 식별 사슬이 실제로 이어지는지 검증한다 — `X-User-Id` 헤더
 * → `SecurityAuditorContextFilter` → `AuditorContext` → `SimpleAuditorAware` → 감사 필드.
 *
 * `AuditingIntegrationTest` 는 `AuditorContext` 에 값을 **직접** 넣으므로 앞의 두 구간을 지나가지
 * 않는다. 그 상태로 두면 필터가 `X-User-Id` 를 컨텍스트에 넣지 않게 돼도 감사 테스트는
 * 통과한다 — **의존을 만들면서 그 의존을 검증하지 않는 것**이다.
 *
 * **실제 `SecurityFilterChain` 을 지난다.** 필터를 직접 호출하면 `addFilterAfter` 등록이
 * 빠지거나 순서가 틀려도 통과한다 — 저장 지점은 permitAll 경로의 테스트 전용 프로브다
 * ([AuditProbeConfiguration]).
 *
 * `test` 프로파일을 켜지 않는다. 켜면 `TestSecurityConfiguration` 이 모든 요청을 permitAll 하고
 * 진짜 `SecurityConfiguration` 이 `@Profile("!test")` 라 아예 뜨지 않는다.
 *
 * 이 스펙은 **신뢰 경계가 있는 배치**를 모사한다 — `app.audit.trust-user-header`
 * 를 켠다. 기본 구성(꺼짐)에서 헤더가 무시되는 것은 `AuditorHeaderDistrustIntegrationTest`
 * 가 본다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.persistence.AuditorChainIntegrationTest"
 * ```
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testcontainers")
@Import(
    IntegrationTestConfiguration::class,
    TestcontainersConfiguration::class,
    AuditProbeConfiguration::class,
)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
@TestPropertySource(properties = ["app.audit.trust-user-header=true"])
class AuditorChainIntegrationTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {

    init {
        afterTest { AuditorContext.clear() }

        Given("X-User-Id 가 42 인 요청이 실제 체인을 지날 때") {
            Then("C-1 생성자·수정자가 42 가 된다") {
                mockMvc
                    .perform(post(AuditProbeConfiguration.PATH).header(Constant.HEADER_USER_ID, "42"))
                    .andExpect(status().isOk)
                    .andExpect(content().string(AuditProbeConfiguration.body(observed = 42L, createdBy = 42L)))
            }

            // 남으면 스레드 풀 재사용 시 다음 요청이 남의 사용자로 감사된다.
            // MockMvc 는 호출한 스레드에서 체인을 돌리므로, 요청이 끝난 뒤 이 스레드를
            // 보는 것이 곧 "필터가 finally 에서 지웠는가" 다.
            Then("C-3 요청이 끝나면 감사 컨텍스트가 정리된다") {
                mockMvc
                    .perform(post(AuditProbeConfiguration.PATH).header(Constant.HEADER_USER_ID, "42"))
                    .andExpect(status().isOk)

                AuditorContext.get().shouldBeNull()
            }
        }

        Given("X-User-Id 가 Int 범위를 넘는 3000000000 인 요청이 실제로 들어올 때") {
            // 식별자 사슬 전체가 Long 이다 — 잘라서 다른 사용자의 ID 로 기록하지 않고 그대로 받는다.
            Then("C-4 생성자·수정자가 3000000000 이 된다") {
                mockMvc
                    .perform(post(AuditProbeConfiguration.PATH).header(Constant.HEADER_USER_ID, "3000000000"))
                    .andExpect(status().isOk)
                    .andExpect(
                        content().string(
                            AuditProbeConfiguration.body(observed = 3_000_000_000L, createdBy = 3_000_000_000L),
                        ),
                    )
            }
        }

        Given("사용자 헤더가 없는 요청이 실제 체인을 지날 때") {
            Then("C-2 생성자·수정자가 SYSTEM_USER_ID 가 된다") {
                mockMvc
                    .perform(post(AuditProbeConfiguration.PATH))
                    .andExpect(status().isOk)
                    .andExpect(
                        content().string(
                            AuditProbeConfiguration.body(observed = null, createdBy = Constant.SYSTEM_USER_ID),
                        ),
                    )
            }
        }
    }
}