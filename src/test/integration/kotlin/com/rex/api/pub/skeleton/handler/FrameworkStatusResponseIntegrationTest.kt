package com.rex.api.pub.skeleton.handler

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 전용 핸들러가 없는 Spring 예외(`ErrorResponse`)는 그 HTTP 상태와 그 상태의 코드(`ErrorCode.fromStatus`)로 응답한다.
 * 본문 `status` 와 HTTP 상태가 같고, 응답 문구에 내부 정보가 없다. 코드가 없는 상태는 400·500 코드와 그 상태로 낮춘다.
 * 예외 발생용 엔드포인트는 [TestFrameworkStatusController] 에 있다.
 */
@SpringBootTest
@ActiveProfiles("test", "testcontainers")
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class FrameworkStatusResponseIntegrationTest(
    private val mockMvc: MockMvc,
) : DescribeSpec() {

    private fun ResultActions.expectError(status: Int, code: String, divisionCode: String, reason: String): ResultActions =
        andExpect(status().`is`(status))
            .andExpect(jsonPath("$.status").value(status))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.divisionCode").value(divisionCode))
            .andExpect(jsonPath("$.reason").value(reason))
            .bodyHasNoInternals()

    init {
        describe("상태 기반 응답 — 상태가 보존된다") {

            it("GET 전용 경로에 DELETE 를 보내면 405 METHOD_NOT_ALLOWED_ERROR 이고 Allow 헤더가 있다") {
                mockMvc
                    .perform(delete("$BASE/get-only"))
                    .expectError(405, "METHOD_NOT_ALLOWED_ERROR", "G017", ErrorReason.METHOD_NOT_ALLOWED)
                    .andExpect(header().string(HttpHeaders.ALLOW, org.hamcrest.Matchers.containsString("GET")))
            }

            it("JSON 전용 POST 에 text/plain 을 보내면 415 UNSUPPORTED_MEDIA_TYPE_ERROR 이고 Accept 헤더가 있다") {
                mockMvc
                    .perform(post("$BASE/json-only").contentType(MediaType.TEXT_PLAIN).content("x"))
                    .expectError(415, "UNSUPPORTED_MEDIA_TYPE_ERROR", "G020", ErrorReason.UNSUPPORTED_MEDIA_TYPE)
                    .andExpect(header().string(HttpHeaders.ACCEPT, org.hamcrest.Matchers.containsString(MediaType.APPLICATION_JSON_VALUE)))
            }

            it("text/plain 만 내는 경로에 Accept: application/json 이면 406 NOT_ACCEPTABLE_ERROR 본문이다") {
                mockMvc
                    .perform(get("$BASE/text-only").accept(MediaType.APPLICATION_JSON))
                    .expectError(406, "NOT_ACCEPTABLE_ERROR", "G018", ErrorReason.NOT_ACCEPTABLE)
            }

            it("업로드 크기 초과는 413 PAYLOAD_TOO_LARGE_ERROR 다") {
                mockMvc
                    .perform(get("$BASE/upload-too-large"))
                    .expectError(413, "PAYLOAD_TOO_LARGE_ERROR", "G019", ErrorReason.PAYLOAD_TOO_LARGE)
            }

            it("비동기 요청 시간 초과는 503 SERVICE_UNAVAILABLE_ERROR 이고 5xx 고정 문장이다") {
                mockMvc
                    .perform(get("$BASE/async-timeout"))
                    .expectError(503, "SERVICE_UNAVAILABLE_ERROR", "G016", ErrorReason.EXTERNAL_SERVICE_ERROR)
            }
        }

        describe("상태 기반 응답 — 코드가 없는 상태는 낮춘다") {

            it("418 은 400 BAD_REQUEST_ERROR 로 응답한다") {
                mockMvc
                    .perform(get("$BASE/teapot"))
                    .expectError(400, "BAD_REQUEST_ERROR", "G001", ErrorReason.BAD_REQUEST)
            }

            it("504 는 500 INTERNAL_SERVER_ERROR 로 응답한다") {
                mockMvc
                    .perform(get("$BASE/gateway-timeout"))
                    .expectError(500, "INTERNAL_SERVER_ERROR", "G999", ErrorReason.SERVER_ERROR)
            }
        }

        describe("ErrorResponse 가 아닌 예외") {

            it("변환기 부재(ConversionNotSupportedException)는 400 이 아니라 500 이다") {
                mockMvc
                    .perform(get("$BASE/conversion-not-supported"))
                    .expectError(500, "INTERNAL_SERVER_ERROR", "G999", ErrorReason.SERVER_ERROR)
            }
        }
    }

    companion object {
        private const val BASE = "/test/framework-status"
    }
}