package com.rex.api.pub.skeleton.handler

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import com.rex.api.pub.skeleton.exception.ApiCallException
import com.rex.api.pub.skeleton.exception.ClientBadRequestException
import com.rex.api.pub.skeleton.exception.InvalidRequestParameterException
import com.rex.api.pub.skeleton.exception.ItemNotFoundException
import com.rex.api.pub.skeleton.exception.auth.AuthorizationException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * GlobalExceptionHandler 통합 테스트.
 *
 * Testcontainers를 통해 데이터베이스와 연동하여 예외 처리를 테스트합니다.
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

                it("should handle ApiCallException with custom status") {
                    mockMvc
                        .perform(get("/test/exception/api-call"))
                        .andExpect(status().isServiceUnavailable)
                        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                        .andExpect(jsonPath("$.reason").value("External service is unavailable"))
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
            }

            describe("Standard Exception Handlers") {

                it("should handle IllegalArgumentException with 400 status") {
                    mockMvc
                        .perform(get("/test/exception/illegal-argument"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.code").value("NOT_VALID_ERROR"))
                        .andExpect(jsonPath("$.reason").value("Invalid argument provided"))
                }

                it("should handle NoSuchElementException with 404 status") {
                    mockMvc
                        .perform(get("/test/exception/no-such-element"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.code").value("NOT_FOUND_ERROR"))
                        .andExpect(jsonPath("$.reason").value("Element not found"))
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
                }

                it("should map AccessDeniedException of an anonymous request to 401 UNAUTHORIZED_ERROR") {
                    mockMvc
                        .perform(get("/test/exception/access-denied").with(anonymous()))
                        .andExpect(status().isUnauthorized)
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ERROR"))
                }

                it("should handle generic Exception with 500 status") {
                    mockMvc
                        .perform(get("/test/exception/generic"))
                        .andExpect(status().isInternalServerError)
                        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                        .andExpect(jsonPath("$.reason").value("Unexpected error occurred"))
                }
            }
        }
    }
}

/**
 * Test controller for exception handling tests
 */
@RestController
@RequestMapping("/test/exception")
class TestExceptionController {
    @GetMapping("/item-not-found")
    fun throwItemNotFoundException(): ResponseEntity<Any> = throw ItemNotFoundException("User with id 123 not found")

    @GetMapping("/client-bad-request")
    fun throwClientBadRequestException(): ResponseEntity<Any> = throw ClientBadRequestException("Invalid client request")

    @GetMapping("/invalid-parameter")
    fun throwInvalidRequestParameterException(): ResponseEntity<Any> = throw InvalidRequestParameterException("Parameter 'id' must be positive")

    @GetMapping("/api-call")
    fun throwApiCallException(): ResponseEntity<Any> = throw ApiCallException("External service is unavailable", HttpStatus.SERVICE_UNAVAILABLE)

    @PostMapping("/validate")
    fun validateRequest(
        @Valid @RequestBody request: TestValidationRequest,
    ): ResponseEntity<Any> = ResponseEntity.ok(request)

    @GetMapping("/illegal-argument")
    fun throwIllegalArgumentException(): ResponseEntity<Any> = throw IllegalArgumentException("Invalid argument provided")

    @GetMapping("/no-such-element")
    fun throwNoSuchElementException(): ResponseEntity<Any> = throw NoSuchElementException("Element not found")

    @GetMapping("/generic")
    fun throwGenericException(): ResponseEntity<Any> = throw RuntimeException("Unexpected error occurred")

    @GetMapping("/authorization")
    fun throwAuthorizationException(): ResponseEntity<Any> = throw AuthorizationException("그룹을 관리할 권한이 없습니다")

    @GetMapping("/access-denied")
    fun throwAccessDeniedException(): ResponseEntity<Any> = throw AccessDeniedException("Access Denied")
}

/**
 * Test request model with @JsonProperty annotations
 * Note: @field:JsonProperty is required for Kotlin data classes to apply annotation to backing field
 */
data class TestValidationRequest(
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(min = 2, max = 50, message = "이름은 2~50자 사이여야 합니다")
    @field:JsonProperty("user_name")
    val userName: String?,
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "유효한 이메일 형식이어야 합니다")
    @field:JsonProperty("user_email")
    val userEmail: String?,
)