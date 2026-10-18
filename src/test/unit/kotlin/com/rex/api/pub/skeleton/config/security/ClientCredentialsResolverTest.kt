package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest
import java.util.Base64

/** 토큰 엔드포인트 클라이언트 자격 — `client_secret_basic`·`client_secret_post` 둘만, 한 요청에 하나만. */
class ClientCredentialsResolverTest : BehaviorSpec({
    val resolver = ClientCredentialsResolver()

    fun basic(value: String) = MockHttpServletRequest().apply { addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(value.toByteArray())) }

    Given("CC-1 Basic 헤더") {
        Then("id·secret 을 꺼낸다") {
            resolver.resolve(basic("client-a:secret-a"), null, null) shouldBe ClientCredentials("client-a", "secret-a")
        }
    }

    Given("CC-2 URL 인코딩된 ':'·'/' 를 담은 시크릿 (RFC 6749 §2.3.1)") {
        Then("각각 form-urlencoded 디코드한다") {
            resolver.resolve(basic("client%3Aa:s3%3Acr%2Ft"), null, null) shouldBe ClientCredentials("client:a", "s3:cr/t")
        }
    }

    Given("CC-3 폼만") {
        Then("폼 값을 그대로 쓴다") {
            resolver.resolve(MockHttpServletRequest(), "client-a", "secret-a") shouldBe ClientCredentials("client-a", "secret-a")
        }
    }

    Given("CC-4 Basic 과 폼 client_secret 동시 전송") {
        Then("invalid_request") {
            shouldThrow<OAuth2Exception> { resolver.resolve(basic("client-a:secret-a"), "client-a", "secret-a") }.error shouldBe "invalid_request"
        }
    }

    Given("CC-5 깨진 Basic") {
        Then("invalid_client") {
            val notBase64 = MockHttpServletRequest().apply { addHeader("Authorization", "Basic !!!") }
            shouldThrow<OAuth2Exception> { resolver.resolve(notBase64, null, null) }.error shouldBe "invalid_client"
            shouldThrow<OAuth2Exception> { resolver.resolve(basic("no-separator"), null, null) }.error shouldBe "invalid_client"
            // 잘못된 % 인코딩 — id·secret 어느 쪽이든 전역 400 이 아니라 invalid_client 다
            shouldThrow<OAuth2Exception> { resolver.resolve(basic("client:%GG"), null, null) }.error shouldBe "invalid_client"
            shouldThrow<OAuth2Exception> { resolver.resolve(basic("%G:secret"), null, null) }.error shouldBe "invalid_client"
        }
    }

    Given("CC-6 폼 client_id 가 Basic 의 id 와 다르면") {
        Then("invalid_request") {
            shouldThrow<OAuth2Exception> { resolver.resolve(basic("client-a:secret-a"), "client-b", null) }.error shouldBe "invalid_request"
        }
    }
})