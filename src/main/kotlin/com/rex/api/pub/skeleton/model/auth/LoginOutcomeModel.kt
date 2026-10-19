package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** API 로그인 결과 — 컨트롤러가 [Tokens] 는 200, [MfaRequired] 는 202 로 낸다. */
sealed interface LoginOutcomeModel {
    data class Tokens(
        @field:JsonProperty("tokens")
        val tokens: TokenResponseModel,
    ) : LoginOutcomeModel

    data class MfaRequired(
        @field:JsonProperty("challenge")
        val challenge: MfaChallengeModel,
    ) : LoginOutcomeModel
}