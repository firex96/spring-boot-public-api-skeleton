package com.rex.api.pub.skeleton.dto.example

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 외부 API 에러 응답 DTO.
 */
data class ExternalErrorResponseDto(
    @field:JsonProperty("error_code")
    val errorCode: String,
    @field:JsonProperty("error_message")
    val errorMessage: String,
    @field:JsonProperty("error_details")
    val errorDetails: String?,
)