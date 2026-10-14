package com.rex.api.pub.skeleton.filter

import com.rex.api.pub.skeleton.constant.Constant
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * `TraceIdFilter` 가 **실제로 무엇을 하는지** 검증한다.
 *
 * 등록 테스트(`TraceIdFilterRegistrationIntegrationTest`)는 빈이 있는지만 본다.
 * 체인 안에서 MDC 가 채워지는지, 끝난 뒤 지워지는지는 보지 않는다.
 *
 * **F-5 가 핵심이다** — 예외 경로에서 MDC 가 남으면 스레드 풀 재사용 시
 * **다음 요청이 남의 traceId 로 로깅된다.**
 */
class TraceIdFilterBehaviorTest : BehaviorSpec({

    afterTest { MDC.clear() }

    fun run(
        header: String?,
        chain: FilterChain,
    ) {
        val request = MockHttpServletRequest()
        header?.let { request.addHeader(Constant.HEADER_TRACE_ID, it) }
        TraceIdFilter().doFilter(request, MockHttpServletResponse(), chain)
    }

    Given("유효한 X-Trace-Id 헤더") {
        Then("F-1 체인 안에서 원값이 보이고, 끝난 뒤에는 지워진다") {
            var seen: String? = null
            run("abc-123") { _, _ -> seen = MDC.get(Constant.KEY_TRACE_ID) }
            seen shouldBe "abc-123"
            MDC.get(Constant.KEY_TRACE_ID).shouldBeNull()
        }
    }

    Given("헤더가 없는 요청") {
        Then("F-2 체인 안에서 새 값이 채워지고, 끝난 뒤에는 지워진다") {
            var seen: String? = null
            run(null) { _, _ -> seen = MDC.get(Constant.KEY_TRACE_ID) }
            seen shouldNotBe null
            seen!!.isNotBlank() shouldBe true
            MDC.get(Constant.KEY_TRACE_ID).shouldBeNull()
        }
    }

    Given("개행이 섞인 헤더") {
        Then("F-3 원값이 아니라 새 값이 쓰인다 — 로그 위조 차단") {
            var seen: String? = null
            run("bad\ninjected") { _, _ -> seen = MDC.get(Constant.KEY_TRACE_ID) }
            seen shouldNotBe "bad\ninjected"
            MDC.get(Constant.KEY_TRACE_ID).shouldBeNull()
        }
    }

    Given("65자를 넘는 헤더") {
        Then("F-4 원값이 아니라 새 값이 쓰인다") {
            val tooLong = "a".repeat(65)
            var seen: String? = null
            run(tooLong) { _, _ -> seen = MDC.get(Constant.KEY_TRACE_ID) }
            seen shouldNotBe tooLong
            MDC.get(Constant.KEY_TRACE_ID).shouldBeNull()
        }
    }

    Given("체인이 예외를 던지는 요청") {
        Then("F-5 예외가 전파되면서도 MDC 는 반드시 지워진다") {
            shouldThrow<IllegalStateException> {
                run("abc-123") { _, _ -> throw IllegalStateException("체인 실패") }
            }
            // 여기가 남아 있으면 스레드 재사용 시 다음 요청이 남의 traceId 로 로깅된다.
            MDC.get(Constant.KEY_TRACE_ID).shouldBeNull()
        }
    }
})