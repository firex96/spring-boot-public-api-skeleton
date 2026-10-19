package com.rex.api.pub.skeleton.dto.auth

/** 1단계 인증의 결과 — 세션을 발급했거나(2차 인증 없음), 2단계 challenge 를 만들었다(2차 인증 켜짐). */
sealed interface LoginOutcomeDto {
    data class Tokens(
        val session: IssuedSessionDto,
    ) : LoginOutcomeDto

    data class MfaRequired(
        val challenge: MfaChallengeDto,
    ) : LoginOutcomeDto
}