package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** `POST /auth/login/mfa` — 1단계가 준 challenge 토큰과 2단계 코드. */
data class MfaLoginRequestDto(
    @field:NotBlank(message = "mfaToken 은 필수입니다")
    val mfaToken: String,
    @field:NotNull(message = "method 는 필수입니다")
    val method: MfaMethod,
    @field:NotBlank(message = "코드는 필수입니다")
    val code: String,
)