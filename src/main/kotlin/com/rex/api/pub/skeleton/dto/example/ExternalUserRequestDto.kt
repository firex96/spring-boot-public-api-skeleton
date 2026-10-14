package com.rex.api.pub.skeleton.dto.example

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 외부 API 요청 DTO.
 */
data class ExternalUserRequestDto(
    @field:JsonProperty("user_id")
    val userId: String,
    @field:JsonProperty("user_name")
    val userName: String,
    @field:JsonProperty("email_address")
    val emailAddress: String?,
    @field:JsonProperty("phone_number")
    val phoneNumber: String?,
    @field:JsonProperty("request_timestamp")
    val requestTimestamp: Long,
)