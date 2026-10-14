package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 성공/실패 응답 Model
 */
data class ResultResponseModel(
    @field:JsonProperty("success")
    val success: Boolean,
    @field:JsonProperty("message")
    val message: String,
)