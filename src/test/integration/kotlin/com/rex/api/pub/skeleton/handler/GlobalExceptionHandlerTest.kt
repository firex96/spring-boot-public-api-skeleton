package com.rex.api.pub.skeleton.handler

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.enums.AccessDenial
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * GlobalExceptionHandler 통합 테스트.
 *
 * Testcontainers를 통해 데이터베이스와 연동하여 예외 처리를 테스트합니다.
 * 예외 발생용 엔드포인트는 [TestExceptionController] 에 있다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.handler.GlobalExceptionHandlerTest"
 * ```
 */
@SpringBootTest
// test 프로필 — 운영 SecurityConfiguration 대신 TestSecurityConfiguration 으로 예외 처리 경로만 본다
@ActiveProfiles("test", "testcontainers")
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration::class, TestcontainersConfiguration::class)
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class GlobalExceptionHandlerTest(
    private val mockMvc: MockMvc,
) : DescribeSpec() {

    init {
        describe("GlobalExceptionHandler") {

            describe("Custom Exception Handlers") {

                it("should handle ItemNotFoundException with 404 status") {
                    mockMvc
                        .perform(get("/test/exception/item-not-found"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.code").value("NOT_FOUND_ERROR"))
                        .andExpect(jsonPath("$.reason").value("User with id 123 not found"))
                }

                it("should handle ClientBadRequestException with 400 status") {
                    mockMvc
                        .perform(get("/test/exception/client-bad-request"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("BAD_REQUEST_ERROR"))
                        .andExpect(jsonPath("$.reason").value("Invalid client request"))
                }

                it("should handle InvalidRequestParameterException with 400 status") {
                    mockMvc
                        .perform(get("/test/exception/invalid-parameter"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("BAD_REQUEST_ERROR"))
                        .andExpect(jsonPath("$.reason").value("Parameter 'id' must be positive"))
                }

                it("should handle ApiCallException with the status of its errorCode and a fixed reason") {
                    mockMvc
                        .perform(get("/test/exception/api-call"))
                        .andExpect(status().isServiceUnavailable)
                        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE_ERROR"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.EXTERNAL_SERVICE_ERROR))
                }

                it("should handle WebClientRequestException as 503 SERVICE_UNAVAILABLE_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/web-client-request"))
                        .andExpect(status().isServiceUnavailable)
                        .andExpect(jsonPath("$.status").value(503))
                        .andExpect(jsonPath("$.divisionCode").value("G016"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.EXTERNAL_SERVICE_ERROR))
                }
            }

            describe("Validation with @JsonProperty mapping") {

                it("should map field names using @JsonProperty in validation errors") {
                    val invalidJson =
                        """
                        {
                            "user_name": "",
                            "user_email": "invalid-email"
                        }
                        """.trimIndent()

                    val result =
                        mockMvc
                            .perform(
                                post("/test/exception/validate")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalidJson),
                            ).andExpect(status().isBadRequest)
                            .andReturn()

                    val responseBody = result.response.contentAsString

                    // @JsonProperty name should be used instead of field name
                    responseBody shouldContain "user_name"
                    responseBody shouldContain "user_email"
                }

                it("should return validation error for missing required fields") {
                    val invalidJson = """{}"""

                    mockMvc
                        .perform(
                            post("/test/exception/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("NOT_VALID_ERROR"))
                }

                it("should keep the 'field: message' reason composition") {
                    val invalidJson = """{"user_name": "kim", "user_email": "invalid-email"}"""

                    mockMvc
                        .perform(
                            post("/test/exception/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson),
                        ).andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.reason").value("user_email: 유효한 이메일 형식이어야 합니다"))
                }

                it("should compose ConstraintViolationException as 'parameter: message'") {
                    mockMvc
                        .perform(get("/test/exception/constraint"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("NOT_VALID_ERROR"))
                        .andExpect(jsonPath("$.reason").value("userName: 이름은 2~50자 사이여야 합니다"))
                }
            }

            describe("Request Shape Handlers") {

                it("should answer a missing header with 400 NOT_VALID_HEADER_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/missing-header"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("NOT_VALID_HEADER_ERROR"))
                        .andExpect(jsonPath("$.divisionCode").value("G012"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.missingHeader("X-Test-Header")))
                }

                it("should name a missing parameter in the reason") {
                    mockMvc
                        .perform(get("/test/exception/missing-param"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER_ERROR"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.missingParameter("size")))
                }

                it("should answer a standalone JacksonException with 400 JACKSON_PROCESS_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/jackson"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("JACKSON_PROCESS_ERROR"))
                        .andExpect(jsonPath("$.divisionCode").value("G007"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.MALFORMED_BODY))
                }
            }

            describe("Standard Exception Handlers") {

                it("should handle IllegalArgumentException with a fixed reason") {
                    mockMvc
                        .perform(get("/test/exception/illegal-argument"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("NOT_VALID_ERROR"))
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.divisionCode").value("G011"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.INVALID_VALUE))
                }

                it("should handle NullPointerException with 500 status in both HTTP and body") {
                    mockMvc
                        .perform(get("/test/exception/null-pointer"))
                        .andExpect(status().isInternalServerError)
                        .andExpect(jsonPath("$.code").value("NULL_POINT_ERROR"))
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.divisionCode").value("G010"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.SERVER_ERROR))
                }

                it("should handle IOException with 500 status in both HTTP and body") {
                    mockMvc
                        .perform(get("/test/exception/io"))
                        .andExpect(status().isInternalServerError)
                        .andExpect(jsonPath("$.code").value("IO_ERROR"))
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.divisionCode").value("G005"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.SERVER_ERROR))
                }

                it("should handle NoSuchElementException with 404 status and a fixed reason") {
                    mockMvc
                        .perform(get("/test/exception/no-such-element"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.code").value("NOT_FOUND_ERROR"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.TARGET_NOT_FOUND))
                }

                it("should handle AuthorizationException with 403 FORBIDDEN_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/authorization").with(user("e2e-member")))
                        .andExpect(status().isForbidden)
                        .andExpect(jsonPath("$.code").value("FORBIDDEN_ERROR"))
                        .andExpect(jsonPath("$.divisionCode").value("G008"))
                }

                it("should map AccessDeniedException of an authenticated user to 403 FORBIDDEN_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/access-denied").with(user("e2e-member")))
                        .andExpect(status().isForbidden)
                        .andExpect(jsonPath("$.code").value("FORBIDDEN_ERROR"))
                        .andExpect(jsonPath("$.divisionCode").value("G008"))
                        .andExpect(jsonPath("$.reason").value(AccessDenial.ACCESS_FORBIDDEN.reason))
                }

                it("should map AccessDeniedException of an anonymous request to 401 UNAUTHORIZED_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/access-denied").with(anonymous()))
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ERROR"))
                        .andExpect(jsonPath("$.divisionCode").value("G015"))
                        .andExpect(jsonPath("$.reason").value(AccessDenial.AUTHENTICATION_REQUIRED.reason))
                }

                it("should handle generic Exception with 500 status and a fixed reason") {
                    mockMvc
                        .perform(get("/test/exception/generic"))
                        .andExpect(status().isInternalServerError)
                        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                        .andExpect(jsonPath("$.reason").value(ErrorReason.SERVER_ERROR))
                }
            }
        }
    }
}