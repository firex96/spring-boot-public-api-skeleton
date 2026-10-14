package com.rex.api.pub.skeleton.dto.user

import jakarta.validation.constraints.NotBlank

data class EmailVerificationConfirmDto(
    @field:NotBlank(message = "토큰은 필수입니다")
    val token: String,
)

// ===== 세션 관리 =====