package com.rex.api.pub.skeleton.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequestDto(
    @field:NotBlank(message = "로그인 ID는 필수입니다")
    @field:Size(min = 4, max = 100, message = "로그인 ID는 4자 이상 100자 이하여야 합니다")
    val loginId: String,
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다")
    val password: String,
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val email: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val phone: String? = null,
)