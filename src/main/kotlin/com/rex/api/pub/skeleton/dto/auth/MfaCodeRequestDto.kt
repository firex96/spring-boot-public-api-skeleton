package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank

/** TOTP 코드 하나 — 활성화·복구 코드 재발급의 재인증. */
data class MfaCodeRequestDto(
    @field:NotBlank(message = "코드는 필수입니다")
    val code: String,
)