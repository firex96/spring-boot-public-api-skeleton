package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.rex.api.pub.skeleton.enums.auth.MfaMethod

/** 202 — 2차 인증이 켜진 계정의 1단계 통과. `POST /auth/login/mfa` 에 [mfaToken] 과 코드를 보낸다. */
data class MfaChallengeModel(
    @field:JsonProperty("mfaToken")
    val mfaToken: String,
    @field:JsonProperty("expiresIn")
    val expiresIn: Long,
    @field:JsonProperty("methods")
    val methods: List<MfaMethod>,
)