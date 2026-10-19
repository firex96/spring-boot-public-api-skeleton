package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin

/** 브라우저 2단계 완료 — 세션에 로그인시킬 principal 과, 이어 갈 곳을 정하는 1단계 출처. */
data class BrowserMfaResultModel(
    @field:JsonProperty("principal")
    val principal: BrowserPrincipalModel,
    @field:JsonProperty("origin")
    val origin: MfaOrigin,
)