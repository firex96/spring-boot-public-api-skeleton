package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.config.audit.AuditProperties
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 인증 주체에서 감사 주체를 뽑아 [AuditorContext] 에 넣는다.
 *
 * **인증이 있으면 인증이 이긴다.** 헤더는 신뢰 경계가 있을 때만 보조로 쓴다 —
 * 그래야 헤더를 직접 넣을 수 있는 호출자가 감사 기록을 위조하지 못한다.
 *
 * | 출처 | 규칙 |
 * |---|---|
 * | 인증 | [SecurityContextHolder] → [AuthenticatedUser.userId] |
 * | 헤더 | `app.audit.trust-user-header` 가 켜졌을 때만 `X-User-Id` |
 * | 없음 | `null` → `SimpleAuditorAware` 가 `SYSTEM_USER_ID` |
 *
 * **`SecurityContext` 가 채워진 뒤에 실행돼야 한다.** `SecurityConfiguration` 이
 * `JwtAuthenticationFilter` 바로 뒤에 등록하고, 서블릿 자동 등록은 꺼 둔다 —
 * 두 번 돌면 인증 전 실행분이 헤더 폴백을 먼저 기록한다.
 *
 * 감사 주체는 `Long` 그대로 흐른다 — 식별자 규약(`.claude/rules/conventions.md` 「데이터베이스」)대로
 * 사슬 전체가 `Long` 이므로 범위 변환·폴백이 없다. 헤더는 `toLongOrNull` 로만 파싱한다.
 */
class SecurityAuditorContextFilter(
    private val auditProperties: AuditProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            AuditorContext.set(fromAuthentication() ?: fromHeaderIfTrusted(request))
            filterChain.doFilter(request, response)
        } finally {
            AuditorContext.clear()
        }
    }

    private fun fromAuthentication(): Long? =
        (SecurityContextHolder.getContext()?.authentication?.principal as? AuthenticatedUser)?.userId

    private fun fromHeaderIfTrusted(request: HttpServletRequest): Long? {
        if (!auditProperties.trustUserHeader) return null
        return request.getHeader(Constant.HEADER_USER_ID)?.toLongOrNull()
    }
}