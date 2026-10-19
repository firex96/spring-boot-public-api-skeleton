package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** 2단계 코드와 그 종류 — 2차 인증 해제의 재인증. */
data class MfaVerifyRequestDto(
    @field:NotNull(message = "method 는 필수입니다")
    val method: MfaMethod,
    @field:NotBlank(message = "코드는 필수입니다")
    val code: String,
)