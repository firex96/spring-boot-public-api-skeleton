package com.rex.api.pub.skeleton.dto.example

import com.rex.api.pub.skeleton.enums.example.RequestStatus
import java.time.LocalDateTime
import java.util.UUID

/**
 * 비동기 프록시 요청 상태 추적 DTO.
 *
 * 비동기 요청의 상태를 추적하여 나중에 결과를 조회할 수 있도록 합니다.
 * 프로덕션 환경에서는 데이터베이스에 저장하는 것을 권장합니다.
 */
data class ProxyRequestStatusDto(
    val requestId: String = UUID.randomUUID().toString(),
    val userId: String,
    val status: RequestStatus,
    val message: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null,
    val retryCount: Int = 0,
    val errorDetails: String? = null,
)