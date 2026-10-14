package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// 권한 생성 요청
data class CreatePermissionRequestDto(
    @field:NotBlank(message = "권한 코드는 필수입니다")
    @field:Size(max = 100, message = "권한 코드는 100자 이하여야 합니다")
    @field:Pattern(
        regexp = "^[a-z][a-z0-9]*:[a-z][a-z0-9]*$",
        message = "권한 코드는 'resource:action' 형식이어야 합니다 (예: user:read)",
    )
    val code: String,
    @field:NotBlank(message = "권한 이름은 필수입니다")
    @field:Size(max = 100, message = "권한 이름은 100자 이하여야 합니다")
    val name: String,
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String? = null,
    @field:Size(max = 50, message = "리소스 타입은 50자 이하여야 합니다")
    val resourceType: String? = null,
    @field:Size(max = 50, message = "액션은 50자 이하여야 합니다")
    val action: String? = null,
)