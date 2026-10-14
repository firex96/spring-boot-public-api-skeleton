package com.rex.api.pub.skeleton.dto.example

import com.rex.api.pub.skeleton.enums.example.RequestStatus

/**
 * 비동기 요청 결과 DTO.
 */
data class AsyncProxyResultDto(
    val requestId: String,
    val success: Boolean,
    val status: RequestStatus,
    val response: InternalUserResponseDto? = null,
    val message: String? = null,
)