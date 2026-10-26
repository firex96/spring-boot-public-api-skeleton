package com.rex.api.pub.skeleton.handler

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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