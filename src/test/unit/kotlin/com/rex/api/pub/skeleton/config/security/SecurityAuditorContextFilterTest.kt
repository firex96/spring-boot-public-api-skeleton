package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.config.audit.AuditProperties
import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * public 의 감사 주체는 **인증에서 나온다.**
 *
 * **S-2 가 이 계약의 핵심이다** — 헤더를 위조해도 인증이 이긴다. 이 레포에만
 * Spring Security 가 있어 이 도출이 가능하다(internal·batch 는 헤더뿐이다).
 *
 * 감사 주체는 `Long` 그대로 흐른다(식별자 규약). **N-4b·S-8 은 Int 범위를 넘는 ID 가
 * 잘리지 않고 그대로 들어가는지**, S-9 는 `Long` 파싱 실패가 잘라 쓰지 않고 비워지는지 본다 —
 * 자르면 **전혀 다른 사용자의 ID** 가 감사에 기록되기 때문이다.
 */
class SecurityAuditorContextFilterTest : BehaviorSpec({

    afterTest {
        AuditorContext.clear()
        SecurityContextHolder.clearContext()
    }

    fun authenticate(userId: Long) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                AuthenticatedUser(userId = userId, sessionId = "s", groups = emptyList(), permissions = emptyList()),
                null,
                emptyList(),
            )
    }

    fun run(
        userHeader: String?,
        trustUserHeader: Boolean = true,
        chain: FilterChain,
    ) {
        val request = MockHttpServletRequest()
        userHeader?.let { request.addHeader(Constant.HEADER_USER_ID, it) }
        SecurityAuditorContextFilter(AuditProperties(trustUserHeader = trustUserHeader))
            .doFilter(request, MockHttpServletResponse(), chain)
    }

    Given("인증만 있을 때") {
        Then("S-1 인증 주체가 감사 주체가 된다") {
            authenticate(42L)
            var seen: Long? = null
            run(null) { _, _ -> seen = AuditorContext.get() }

            seen shouldBe 42L
        }
    }

    Given("인증과 다른 값의 헤더가 함께 올 때") {
        // 헤더를 직접 넣을 수 있는 호출자가 감사를 위조하지 못해야 한다.
        Then("S-2 인증이 이긴다") {
            authenticate(42L)
            var seen: Long? = null
            run("99") { _, _ -> seen = AuditorContext.get() }

            seen shouldBe 42L
        }
    }

    Given("인증이 없고 플래그가 켜졌을 때") {
        Then("S-3 헤더로 폴백한다") {
            var seen: Long? = null
            run("99") { _, _ -> seen = AuditorContext.get() }

            seen shouldBe 99L
        }
    }

    Given("인증이 없고 플래그가 꺼졌을 때") {
        Then("S-4 비운다 — 기본값은 헤더를 믿지 않는다") {
            var seen: Long? = 999
            run("99", trustUserHeader = false) { _, _ -> seen = AuditorContext.get() }

            seen.shouldBeNull()
        }
    }

    Given("체인이 예외를 던질 때") {
        Then("S-5 예외가 전파되면서도 반드시 정리된다") {
            authenticate(42L)

            shouldThrow<IllegalStateException> {
                run(null) { _, _ -> error("체인 실패") }
            }

            AuditorContext.get().shouldBeNull()
        }
    }

    Given("인증이 없고 헤더가 빈 문자열일 때") {
        Then("S-6 비운다") {
            var seen: Long? = 999
            run("") { _, _ -> seen = AuditorContext.get() }

            seen.shouldBeNull()
        }
    }

    Given("인증이 없고 헤더가 숫자가 아닐 때") {
        Then("S-7 비운다") {
            var seen: Long? = 999
            run("abc") { _, _ -> seen = AuditorContext.get() }

            seen.shouldBeNull()
        }
    }

    Given("인증이 없고 신뢰된 헤더가 Int 범위를 넘을 때") {
        Then("S-8 Long 으로 그대로 받는다 — 식별자 사슬 전체가 Long 이라 범위 변환이 없다") {
            var seen: Long? = null
            run("2147483648") { _, _ -> seen = AuditorContext.get() }

            seen shouldBe 2147483648L
        }
    }

    Given("인증이 없고 신뢰된 헤더가 Long 범위를 넘을 때") {
        Then("S-9 비운다 — 파싱 실패는 잘라 쓰지 않는다") {
            var seen: Long? = 999
            run("99999999999999999999") { _, _ -> seen = AuditorContext.get() }

            seen.shouldBeNull()
        }
    }

    Given("인증 주체의 userId 가 Int 범위를 넘을 때") {
        Then("N-4b Long 그대로 감사 컨텍스트에 들어간다") {
            authenticate(Int.MAX_VALUE.toLong() + 1L)
            var seen: Long? = null
            run(null) { _, _ -> seen = AuditorContext.get() }

            seen shouldBe Int.MAX_VALUE.toLong() + 1L
        }
    }
})