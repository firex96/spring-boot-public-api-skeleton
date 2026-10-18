package com.rex.api.pub.skeleton.config.security.browser

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 브라우저 세션의 principal 이 가리키는 사용자 세션이 아직 살아 있는지 요청마다 본다.
 *
 * 로그아웃·전체 로그아웃·관리자 강제 종료는 사용자 세션을 폐기한다. 브라우저 세션은 그 사용자 세션 ID 를 들고 있으므로,
 * 여기서 폐기를 보면 브라우저 세션도 버린다 — 그러지 않으면 로그아웃한 사용자가 브라우저 세션으로 계속 인가 코드를 받는다.
 *
 * 빈이 아니다 — `SecurityConfiguration` 이 브라우저 체인 안에서 직접 만든다(빈이면 서블릿 필터로도 자동 등록돼 API 경로에서도 돈다).
 */
class BrowserSessionValidationFilter(
    private val authBizService: AuthBizService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is AuthenticatedUser) {
            val session = authBizService.validateSession(principal.sessionId)
            if (session == null || session.revoked) {
                request.getSession(false)?.invalidate()
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }
}