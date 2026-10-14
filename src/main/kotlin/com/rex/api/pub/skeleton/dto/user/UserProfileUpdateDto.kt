package com.rex.api.pub.skeleton.dto.user

import jakarta.validation.constraints.Size

data class UserProfileUpdateDto(
    @field:Size(max = 100, message = "이름은 100자 이하여야 합니다")
    val name: String?,

    @field:Size(max = 100, message = "닉네임은 100자 이하여야 합니다")
    val nickname: String?,

    @field:Size(max = 20, message = "전화번호는 20자 이하여야 합니다")
    val phone: String?,

    @field:Size(max = 500, message = "프로필 이미지 URL은 500자 이하여야 합니다")
    val profileImage: String?,
)