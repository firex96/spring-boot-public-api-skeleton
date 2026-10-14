package com.rex.api.pub.skeleton.dto.oauth2

import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep

/**
 * `/oauth2/authorize`·`/oauth2/consent` 의 판정 — BizService 가 결정하고 컨트롤러는 리다이렉트 URL 만 조립한다.
 * `CODE_ISSUED` 면 `code`, `ERROR` 면 `error`·`errorDescription` 이 채워진다.
 */
data class AuthorizeDecisionDto(
    val nextStep: AuthorizeNextStep,
    val code: String?,
    val error: String?,
    val errorDescription: String?,
)