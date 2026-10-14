package com.rex.api.pub.skeleton.util

import com.rex.api.pub.skeleton.config.security.SecurityProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

/**
 * 로그인 IP 의 신뢰 경계 — 로그인·소셜 로그인·비밀번호 변경이 쓰는 클라이언트 IP 는 이 해석기 하나가 정한다.
 *
 * CI-1 직접 연결에서 위조한 `X-Forwarded-For` 는 무시되고 `remoteAddr` 이 IP 다(차단 우회·타인 오차단 불가) ·
 * CI-2 신뢰 프록시(CIDR) 뒤에서만 헤더 첫 값을 인정 · CI-3 `trustAllProxies` 는 개발용 전면 신뢰 · CI-4 `unknown`·빈 값은 건너뛴다.
 */
class ClientIpResolverTest : BehaviorSpec({
    fun request(remoteAddr: String, vararg headers: Pair<String, String>) =
        MockHttpServletRequest().apply {
            this.remoteAddr = remoteAddr
            headers.forEach { (k, v) -> addHeader(k, v) }
        }

    Given("신뢰 프록시가 비어 있는 기본 설정") {
        val resolver = ClientIpResolver(SecurityProperties())

        Then("CI-1 직접 연결의 X-Forwarded-For 는 무시되고 remoteAddr 이 클라이언트 IP 다") {
            resolver.resolve(request("198.51.100.7", "X-Forwarded-For" to "203.0.113.10, 10.0.0.1")) shouldBe "198.51.100.7"
        }

        Then("CI-1' 헤더가 없어도 remoteAddr") {
            resolver.resolve(request("198.51.100.7")) shouldBe "198.51.100.7"
        }
    }

    Given("10.0.0.0/8 을 신뢰 프록시로 둔 설정") {
        val resolver = ClientIpResolver(SecurityProperties(trustedProxies = listOf("10.0.0.0/8")))

        Then("CI-2 신뢰 프록시에서 온 요청은 X-Forwarded-For 의 첫 값이 클라이언트 IP 다") {
            resolver.resolve(request("10.1.2.3", "X-Forwarded-For" to "203.0.113.10, 10.1.2.3")) shouldBe "203.0.113.10"
        }

        Then("CI-2' 신뢰 범위 밖 프록시의 헤더는 무시된다") {
            resolver.resolve(request("192.0.2.9", "X-Forwarded-For" to "203.0.113.10")) shouldBe "192.0.2.9"
        }

        Then("CI-4 unknown·빈 값은 건너뛰고 다음 헤더 또는 remoteAddr 로 간다") {
            resolver.resolve(request("10.1.2.3", "X-Forwarded-For" to "unknown", "X-Real-IP" to "203.0.113.11")) shouldBe "203.0.113.11"
            resolver.resolve(request("10.1.2.3", "X-Forwarded-For" to " ")) shouldBe "10.1.2.3"
        }
    }

    Given("trustAllProxies = true (개발용)") {
        val resolver = ClientIpResolver(SecurityProperties(trustAllProxies = true))

        Then("CI-3 어디서 왔든 X-Forwarded-For 첫 값을 쓴다") {
            resolver.resolve(request("198.51.100.7", "X-Forwarded-For" to "203.0.113.10")) shouldBe "203.0.113.10"
        }
    }
})