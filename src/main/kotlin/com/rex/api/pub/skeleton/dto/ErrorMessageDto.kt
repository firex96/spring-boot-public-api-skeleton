package com.rex.api.pub.skeleton.dto

/**
 * 에러 메시지 정보를 담는 DTO.
 * 서비스 레이어에서 에러 메시지 전달에 사용.
 * 다국어 지원 또는 상세 에러 메시지 제공에 활용.
 */
data class ErrorMessageDto(
    val code: String?,
    val values: List<String>,
)