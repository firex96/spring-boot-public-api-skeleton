package com.rex.api.pub.skeleton.dto.example

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 외부 API 응답 DTO.
 */
data class ExternalUserResponseDto(
    @field:JsonProperty("result_code")
    val resultCode: String,
    @field:JsonProperty("result_message")
    val resultMessage: String,
    @field:JsonProperty("user_data")
    val userData: ExternalUserDataDto?,
)