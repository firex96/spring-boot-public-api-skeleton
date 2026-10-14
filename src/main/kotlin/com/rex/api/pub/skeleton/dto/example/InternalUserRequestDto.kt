package com.rex.api.pub.skeleton.dto.example

/**
 * 내부 시스템 사용자 등록 요청 DTO.
 */
data class InternalUserRequestDto(
    val userId: String,
    val userName: String,
    val email: String?,
    val phone: String?,
)