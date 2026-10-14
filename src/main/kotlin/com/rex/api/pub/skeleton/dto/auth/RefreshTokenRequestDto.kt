package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequestDto(
    @field:NotBlank(message = "리프레시 토큰은 필수입니다")
    val refreshToken: String,
)