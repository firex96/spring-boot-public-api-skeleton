package com.rex.api.pub.skeleton.config.client

import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.filter.TraceIdSanitizer
import org.slf4j.MDC
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import reactor.core.publisher.Mono

/**
 * MDC 의 traceId 를 아웃바운드 호출 헤더로 전파한다.
 *
 * 헤더 이름은 [Constant.HEADER_TRACE_ID] 로, 수신 측 TraceIdFilter 가 읽는 이름과 같다.
 * MDC 에 값이 없으면 헤더를 붙이지 않는다.
 */
object TraceIdPropagationFilter {
    fun create(): ExchangeFilterFunction =
        ExchangeFilterFunction.ofRequestProcessor { request ->
            val traceId = MDC.get(Constant.KEY_TRACE_ID)
            if (!TraceIdSanitizer.isValid(traceId)) {
                Mono.just(request)
            } else {
                Mono.just(
                    ClientRequest
                        .from(request)
                        .headers { it.set(Constant.HEADER_TRACE_ID, traceId) }
                        .build(),
                )
            }
        }
}