package com.rex.api.pub.skeleton.config.security.browser

/**
 * 브라우저 체인(세션·CSRF)을 타는 경로의 정본. 나머지는 API 체인(STATELESS·Bearer)이다.
 *
 * 사용자 에이전트가 리다이렉트로 오가는 흐름만 여기에 둔다 — 소셜 로그인 시작·콜백, 콜백 토큰 교환,
 * 인가 서버의 authorize·로그인·동의, 콜백 데모 페이지(교환 API 의 CSRF 토큰을 받는다).
 */
object BrowserSecurityPaths {
    const val SOCIAL_AUTHORIZE = "/auth/oauth2/*/authorize"
    const val SOCIAL_CALLBACK = "/auth/oauth2/*/callback"
    const val SESSION_API = "/auth/session/**"
    const val OAUTH2_AUTHORIZE = "/oauth2/authorize"
    const val OAUTH2_LOGIN = "/oauth2/login"
    const val OAUTH2_CONSENT = "/oauth2/consent"
    const val DEMO_CALLBACK = "/demo/auth/callback"

    val PATHS: Array<String> = arrayOf(SOCIAL_AUTHORIZE, SOCIAL_CALLBACK, SESSION_API, OAUTH2_AUTHORIZE, OAUTH2_LOGIN, OAUTH2_CONSENT, DEMO_CALLBACK)
}