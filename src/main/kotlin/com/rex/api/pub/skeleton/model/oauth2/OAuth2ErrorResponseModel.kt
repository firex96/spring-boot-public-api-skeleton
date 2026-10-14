package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty

/** OAuth2 오류 응답 — `OAuth2AuthorizationController.@ExceptionHandler`. 필드 이름은 기존 DTO 계약 그대로. */
data class OAuth2ErrorResponseModel(
    @field:JsonProperty("error")
    val error: String,
    @field:JsonProperty("errorDescription")
    val errorDescription: String?,
    @field:JsonProperty("errorUri")
    val errorUri: String?,
)