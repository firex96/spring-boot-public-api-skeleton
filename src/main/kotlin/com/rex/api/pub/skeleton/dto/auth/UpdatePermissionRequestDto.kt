package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 권한 수정 요청
data class UpdatePermissionRequestDto(
    @field:NotBlank(message = "권한 이름은 필수입니다")
    @field:Size(max = 100, message = "권한 이름은 100자 이하여야 합니다")
    val name: String,
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다")
    val description: String? = null,
)