package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** 브라우저 로그인 결과 — [SignedIn] 은 세션에 로그인시키고, [MfaRequired] 는 토큰을 세션에 두고 `/auth/mfa` 로 보낸다. */
sealed interface BrowserLoginOutcomeModel {
    data class SignedIn(
        @field:JsonProperty("principal")
        val principal: BrowserPrincipalModel,
    ) : BrowserLoginOutcomeModel

    data class MfaRequired(
        @field:JsonProperty("mfaToken")
        val mfaToken: String,
    ) : BrowserLoginOutcomeModel
}