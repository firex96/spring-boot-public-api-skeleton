package com.rex.api.pub.skeleton.enums.oauth2

/** OAuth2 인가 요청의 다음 단계 — 컨트롤러 리다이렉트 분기 키. */
enum class AuthorizeNextStep {
    LOGIN_REQUIRED,
    CONSENT_REQUIRED,
    CODE_ISSUED,
    ERROR,
}