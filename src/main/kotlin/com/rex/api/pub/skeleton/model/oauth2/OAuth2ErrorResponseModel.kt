package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty

/** OAuth2 오류 응답 — `OAuth2AuthorizationController.@ExceptionHandler`. 키는 RFC 6749 §5.2 (`error_description`·`error_uri`). */
data class OAuth2ErrorResponseModel(
    @field:JsonProperty("error")
    val error: String,
    @field:JsonProperty("error_description")
    val errorDescription: String?,
    @field:JsonProperty("error_uri")
    val errorUri: String?,
)