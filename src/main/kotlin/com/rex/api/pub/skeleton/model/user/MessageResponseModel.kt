package com.rex.api.pub.skeleton.model.user

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 공통 응답 Model
 */
data class MessageResponseModel(
    @field:JsonProperty("message")
    val message: String,
)