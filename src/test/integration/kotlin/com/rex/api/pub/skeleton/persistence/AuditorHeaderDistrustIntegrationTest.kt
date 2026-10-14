package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import com.rex.api.pub.skeleton.support.AuditProbeConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * **기본 구성에서 `X-User-Id` 헤더가 감사 주체로 쓰이지 않는지** 검증한다.
 *
 * `app.audit.trust-user-header` 를 **override 하지 않는다** — 컨텍스트 기본값
 * 그대로다. 그래서 이 스펙이 통과하면 「스켈레톤에서 새로 만든 프로젝트는 헤더를
 * 신뢰하지 않는다」가 확인된다.
 *
 * **위조 헤더 테스트다.** 호출자가 남의 사용자 ID 를 헤더에 넣어도 감사 기록이
 * 오염되지 않아야 한다.
 *
 * **실제 `SecurityFilterChain` 을 지난다** — `AuditorChainIntegrationTest` 와 같은 프로브·
 * 같은 경로를 쓰고, 다른 것은 속성 override 가 없다는 것뿐이다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.persistence.AuditorHeaderDistrustIntegrationTest"
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
class AuditorHeaderDistrustIntegrationTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {

    init {
        afterTest { AuditorContext.clear() }

        Given("X-User-Id 가 42 인 요청이 기본 구성의 실제 체인을 지날 때") {
            // 헤더를 신뢰하지 않으므로 42 가 아니라 시스템이 주체가 된다.
            Then("C-4 생성자·수정자가 SYSTEM_USER_ID 가 된다") {
                mockMvc
                    .perform(post(AuditProbeConfiguration.PATH).header(Constant.HEADER_USER_ID, "42"))
                    .andExpect(status().isOk)
                    .andExpect(
                        content().string(
                            AuditProbeConfiguration.body(observed = null, createdBy = Constant.SYSTEM_USER_ID),
                        ),
                    )
            }

            // C-4 만으로는 약하다 — SYSTEM_USER_ID 가 0 이고 createdBy 의 Kotlin
            // 기본값도 0 이라 감사 리스너가 아예 안 붙어도 통과한다.
            // 체인 안에서 감사 컨텍스트가 비어 있음을 함께 봐야 "필터가 안 넣었다" 가 확정된다.
            Then("C-5 체인 안에서 감사 컨텍스트가 비어 있다") {
                val body =
                    mockMvc
                        .perform(post(AuditProbeConfiguration.PATH).header(Constant.HEADER_USER_ID, "42"))
                        .andExpect(status().isOk)
                        .andReturn()
                        .response.contentAsString

                body shouldStartWith "null:"
            }
        }
    }
}