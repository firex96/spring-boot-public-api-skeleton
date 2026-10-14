package com.rex.api.pub.skeleton.dto.security

import jakarta.validation.constraints.NotBlank

data class UnlockAccountRequestDto(
    @field:NotBlank(message = "사용자명은 필수입니다")
    val username: String,
)