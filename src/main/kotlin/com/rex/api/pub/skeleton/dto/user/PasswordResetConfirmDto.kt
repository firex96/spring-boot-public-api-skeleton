package com.rex.api.pub.skeleton.dto.user

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetConfirmDto(
    @field:NotBlank(message = "토큰은 필수입니다")
    val token: String,

    @field:NotBlank(message = "새 비밀번호는 필수입니다")
    @field:Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다")
    val newPassword: String,
)