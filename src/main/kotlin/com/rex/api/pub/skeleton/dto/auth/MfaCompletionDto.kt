package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.MfaOrigin

/** 2단계 완료 결과 — 발급한 세션과, 이어 갈 곳을 정하는 1단계 출처. */
data class MfaCompletionDto(
    val origin: MfaOrigin,
    val session: IssuedSessionDto,
)