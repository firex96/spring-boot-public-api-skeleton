package com.rex.api.pub.skeleton.handler

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 오류 응답의 **외부 계약**을 고정한다.
 *
 * 두 가지를 본다.
 *
 * 1. `status`·`divisionCode`·`resultMessage` 가 **응답 본문에 실린다.**
 *    세 필드는 `private val` 로 선언돼 public getter 가 없었고, Jackson 기본 가시성은
 *    public getter 만 보므로 **소비자가 받은 적이 없었다.** `divisionCode` 체계
 *    (`G002`·`G007` 등)가 통째로 죽어 있었다.
 * 2. 「본문 누락」과 「본문 형식 오류」가 **구분된다.**
 *    둘 다 400 이지만 소비자가 재시도 여부를 판단하려면 달라야 한다.
 *
 * **기존 계약이 바뀌지 않는 것도 같이 단언한다** — 필드 추가는 additive 여야 한다.
 */
@SpringBootTest
// test 프로필 — 운영 SecurityConfiguration 대신 TestSecurityConfiguration 으로 예외 처리 경로만 본다
@ActiveProfiles("test", "testcontainers")
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class ErrorResponseContractIntegrationTest(
    private val mockMvc: MockMvc,
) : DescribeSpec() {
    init {
        describe("오류 응답 계약") {

            it("status·divisionCode·resultMessage 가 응답에 실린다") {
                mockMvc
                    .perform(get("/test/exception/item-not-found"))
                    .andExpect(status().isNotFound)
                    // 새로 노출되는 세 필드
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.divisionCode").exists())
                    .andExpect(jsonPath("$.resultMessage").exists())
                    // 기존 계약 불변 — 필드 추가가 기존 소비자를 깨뜨리지 않는다
                    .andExpect(jsonPath("$.code").value("NOT_FOUND_ERROR"))
                    .andExpect(jsonPath("$.reason").value("User with id 123 not found"))
            }

            it("본문이 없으면 REQUEST_BODY_MISSING_ERROR 다") {
                mockMvc
                    .perform(
                        post("/test/exception/validate")
                            .contentType(MediaType.APPLICATION_JSON),
                    )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("REQUEST_BODY_MISSING_ERROR"))
            }

            it("본문이 깨진 JSON 이면 JACKSON_PROCESS_ERROR 다") {
                mockMvc
                    .perform(
                        post("/test/exception/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"user_name\":"),
                    )
                    .andExpect(status().isBadRequest)
                    // 「누락」이 아니라 「형식 오류」로 구분된다
                    .andExpect(jsonPath("$.code").value("JACKSON_PROCESS_ERROR"))
                    .andExpect(jsonPath("$.divisionCode").exists())
            }
        }

        // 본문 status 와 HTTP 상태는 같은 곳(ErrorCode.status)에서 정해진다 — 갈라지면 소비자가 어느 쪽을 믿어야 할지 모른다.
        describe("본문 status 는 HTTP 상태와 같다") {

            it("검증 실패는 400 이고 본문 status 도 400 이다") {
                mockMvc
                    .perform(
                        post("/test/exception/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"),
                    )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("NOT_VALID_ERROR"))
                    .andExpect(jsonPath("$.divisionCode").value("G011"))
            }

            it("외부 API 응답 불가는 503 SERVICE_UNAVAILABLE_ERROR 다") {
                mockMvc
                    .perform(get("/test/exception/api-call"))
                    .andExpect(status().isServiceUnavailable)
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE_ERROR"))
                    .andExpect(jsonPath("$.divisionCode").value("G016"))
            }

            it("외부 API 호출 실패의 기본은 502 EXTERNAL_API_ERROR 다") {
                mockMvc
                    .perform(get("/test/exception/api-call-default"))
                    .andExpect(status().isBadGateway)
                    .andExpect(jsonPath("$.status").value(502))
                    .andExpect(jsonPath("$.divisionCode").value("G014"))
            }

            it("CodedException 을 바로 던지면 그 코드의 상태로 응답한다") {
                mockMvc
                    .perform(get("/test/exception/coded"))
                    .andExpect(status().isConflict)
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CONFLICT_ERROR"))
                    .andExpect(jsonPath("$.divisionCode").value("G008A"))
                    .andExpect(jsonPath("$.reason").value("이미 처리된 요청입니다"))
            }
        }
    }
}