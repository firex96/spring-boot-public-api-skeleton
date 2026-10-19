package com.rex.api.pub.skeleton.config.security.browser

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * 브라우저 2단계를 기다리는 challenge 토큰을 세션에 둔다 — **문자열 토큰만**. 대기 상태 자체(사용자·출처·실패 수·만료)는 Redis challenge 에 있고,
 * 1회 소비도 거기서 판정한다(세션 속성은 요청마다 읽은 사본이라 동시 요청이 둘 다 성공할 수 있다).
 */
@Component
class MfaPendingTokenStore {
    /**
     * 2단계 대기를 시작한다 — 토큰을 두고 **세션 ID 를 바꾼다**(1단계를 넘긴 세션의 고정 공격 차단). 로그인시키지는 않는다.
     * 진입점(인가 서버 로그인·소셜 콜백)이 늘어도 회전을 빠뜨리지 않게 둘을 한 메서드에 둔다(`BrowserSessionAuthenticator.signIn` 과 같은 형태).
     */
    fun begin(request: HttpServletRequest, mfaToken: String) {
        request.getSession(true).setAttribute(ATTRIBUTE, mfaToken)
        request.changeSessionId()
    }

    fun peek(request: HttpServletRequest): String? = request.getSession(false)?.getAttribute(ATTRIBUTE) as String?

    fun clear(request: HttpServletRequest) {
        request.getSession(false)?.removeAttribute(ATTRIBUTE)
    }

    companion object {
        const val ATTRIBUTE = "MFA_PENDING_TOKEN"
    }
}