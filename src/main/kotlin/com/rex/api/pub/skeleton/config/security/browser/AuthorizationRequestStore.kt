package com.rex.api.pub.skeleton.config.security.browser

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * 로그인이 필요해 멈춘 authorize 요청을 브라우저 세션에 잠시 둔다 — 로그인 성공 뒤 그 URL 로 돌아간다.
 *
 * 복귀 주소를 쿼리(`redirect_to`)로 받지 않는 이유: 그러면 누구나 로그인 뒤 이동할 곳을 정할 수 있다(오픈 리다이렉트).
 * 저장하는 값은 서버가 검증된 파라미터로 다시 조립한 `/oauth2/authorize?` URL 뿐이다.
 */
@Component
class AuthorizationRequestStore {
    fun save(request: HttpServletRequest, url: String) {
        require(url.startsWith(AUTHORIZE_PREFIX)) { "authorize URL 만 저장한다: $url" }
        request.getSession(true).setAttribute(ATTRIBUTE, url)
    }

    fun take(request: HttpServletRequest): String? {
        val session = request.getSession(false) ?: return null
        val url = session.getAttribute(ATTRIBUTE) as String? ?: return null
        session.removeAttribute(ATTRIBUTE)
        return url
    }

    companion object {
        const val ATTRIBUTE = "PENDING_AUTHORIZATION_REQUEST"
        private const val AUTHORIZE_PREFIX = "${BrowserSecurityPaths.OAUTH2_AUTHORIZE}?"
    }
}