package com.rex.api.pub.skeleton.filter

import com.rex.api.pub.skeleton.constant.Constant
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        try {
            val httpRequest = request as? HttpServletRequest

            // X-Trace-Id 헤더가 있으면 검증 후 사용, 없거나 형식이 어긋나면 새로 생성.
            // 검증 없이 통과시키면 외부 입력이 로그와 하위 호출 trace 를 오염시킨다.
            val traceId = TraceIdSanitizer.sanitize(httpRequest?.getHeader(Constant.HEADER_TRACE_ID))
            MDC.put(Constant.KEY_TRACE_ID, traceId)

            // 감사 주체는 이 필터가 다루지 않는다 — 로깅 컨텍스트와 채널이 다르다.
            // <BASE>.context.AuditorContext 를 채우는 것은 레포별 전용 필터다.

            chain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}