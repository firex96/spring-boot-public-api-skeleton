package com.rex.api.pub.skeleton.dto.example

/**
 * 내부 시스템 사용자 등록 응답 DTO.
 */
data class InternalUserResponseDto(
    val success: Boolean,
    val message: String,
    val user: InternalUserDataDto?,
)