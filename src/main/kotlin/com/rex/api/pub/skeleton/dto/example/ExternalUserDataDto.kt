package com.rex.api.pub.skeleton.dto.example

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 외부 API 사용자 데이터 DTO.
 */
data class ExternalUserDataDto(
    @field:JsonProperty("user_id")
    val userId: String,
    @field:JsonProperty("registration_date")
    val registrationDate: String?,
    @field:JsonProperty("status_code")
    val statusCode: String,
)