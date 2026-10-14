package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// 역할 생성 요청
data class CreateRoleRequestDto(
    @field:NotBlank(message = "역할 코드는 필수입니다")
    @field:Size(max = 50, message = "역할 코드는 50자 이하여야 합니다")
    @field:Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "역할 코드는 대문자, 숫자, 언더스코어만 사용 가능합니다")
    val code: String,
    @field:NotBlank(message = "역할 이름은 필수입니다")
    @field:Size(max = 100, message = "역할 이름은 100자 이하여야 합니다")
    val name: String,
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String? = null,
    val isDefault: Boolean = false,
)