package com.rex.api.pub.skeleton.enums.auth

/**
 * 2단계 challenge 를 만든 1단계 경로 — 2단계를 마친 뒤 어디로 이어 갈지와, 어느 완료 엔드포인트가 이 challenge 를 받을지를 정한다.
 *
 * API(`POST /auth/login/mfa`)는 [API_PASSWORD]·[API_SOCIAL], 브라우저(`POST /auth/mfa`)는 [AUTHORIZE]·[SOCIAL_BROWSER] 만 받는다.
 */
enum class MfaOrigin {
    API_PASSWORD, // POST /auth/login
    API_SOCIAL, // POST /auth/oauth2/{provider}/login (네이티브 코드 교환)
    AUTHORIZE, // POST /oauth2/login (인가 서버 로그인 화면)
    SOCIAL_BROWSER, // GET /auth/oauth2/{provider}/callback
    ;

    val isSocial: Boolean get() = this == API_SOCIAL || this == SOCIAL_BROWSER

    companion object {
        val API: Set<MfaOrigin> = setOf(API_PASSWORD, API_SOCIAL)
        val BROWSER: Set<MfaOrigin> = setOf(AUTHORIZE, SOCIAL_BROWSER)
    }
}