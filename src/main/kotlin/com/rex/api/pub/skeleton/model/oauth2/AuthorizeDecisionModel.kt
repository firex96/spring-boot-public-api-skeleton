package com.rex.api.pub.skeleton.model.oauth2

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep

/** `/oauth2/authorize`·`/oauth2/consent` 의 판정 — JSON 응답이 아니라 컨트롤러 리다이렉트 분기용. */
data class AuthorizeDecisionModel(
    @field:JsonProperty("nextStep")
    val nextStep: AuthorizeNextStep,
    @field:JsonProperty("code")
    val code: String?,
    @field:JsonProperty("error")
    val error: String?,
    @field:JsonProperty("errorDescription")
    val errorDescription: String?,
)