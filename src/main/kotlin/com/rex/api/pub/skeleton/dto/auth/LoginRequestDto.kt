package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.NotBlank

data class LoginRequestDto(
    @field:NotBlank(message = "로그인 ID는 필수입니다")
    val loginId: String,
    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String,
    val deviceType: String? = null,
    val deviceInfo: String? = null,
)