package com.rex.api.pub.skeleton.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

/**
 * `WebClientUtil` 의 **동작 계약**을 고정한다.
 *
 * 세 스켈레톤이 이 클래스를 각자 복제 보유했고 본문이 서로 달랐다. 통일하면서
 * 「동작은 같다」고 판단했는데 **그 판단을 고정하는 테스트가 없었다** — 기존 테스트는
 * `WebClientUtil` 자체를 mock 으로 대체해 이 클래스를 전혀 검증하지 않는다.
 *
 * `ExchangeFunction` 을 스텁으로 갈아끼워 HTTP 없이 응답을 만든다.
 */
class WebClientUtilTest : BehaviorSpec({

    fun utilReturning(
        status: HttpStatus,
        body: String,
        counter: AtomicInteger? = null,
    ): WebClientUtil {
        val exchange =
            ExchangeFunction { _: ClientRequest ->
                counter?.incrementAndGet()
                val builder = ClientResponse.create(status)
                if (body.isNotEmpty()) {
                    builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body)
                }
                Mono.just(builder.build())
            }
        return WebClientUtil(WebClient.builder().exchangeFunction(exchange))
    }

    fun post(
        util: WebClientUtil,
        retries: Int = 1,
    ) = util
        .postAsync(
            "http://stub",
            "/x",
            HttpHeaders(),
            mapOf("a" to 1),
            SampleResponse::class.java,
            maxRetryCount = retries,
            waitRetryTime = 1,
        ).block()

    fun causeChain(ex: Throwable) = generateSequence(ex) { it.cause }

    Given("본문이 있는 200 응답") {
        Then("W-1 역직렬화된 객체를 반환한다") {
            val util = utilReturning(HttpStatus.OK, """{"value":"ok"}""")
            util.get("http://stub", "/x", SampleResponse::class.java)?.value shouldBe "ok"
        }
    }

    Given("본문이 없는 204 응답") {
        Then("W-2 null 을 반환한다 — blockOptional().orElse(null) 계약") {
            val util = utilReturning(HttpStatus.NO_CONTENT, "")
            util.get("http://stub", "/x", SampleResponse::class.java).shouldBeNull()
        }
    }

    Given("5xx 응답 · 본문에 즉시실패 표식(500X)") {
        Then("W-3 NonRetryableException 으로 실패한다") {
            val ex = shouldThrow<Exception> { post(utilReturning(HttpStatus.INTERNAL_SERVER_ERROR, "500X 즉시 실패")) }
            causeChain(ex).any { it is WebClientUtil.NonRetryableException } shouldBe true
        }

        Then("W-3b 재시도하지 않는다 — 요청은 한 번뿐이다") {
            val calls = AtomicInteger(0)
            shouldThrow<Exception> {
                post(utilReturning(HttpStatus.INTERNAL_SERVER_ERROR, "500X 즉시 실패", calls), retries = 2)
            }
            calls.get() shouldBe 1
        }
    }

    Given("5xx 응답 · 일반 오류") {
        Then("W-4 RetryableException 으로 실패한다") {
            val ex = shouldThrow<Exception> { post(utilReturning(HttpStatus.INTERNAL_SERVER_ERROR, "일시 오류")) }
            causeChain(ex).any { it is WebClientUtil.RetryableException } shouldBe true
        }

        Then("W-5 설정한 횟수만큼 재시도한다 — 최초 1회 + 재시도 2회") {
            val calls = AtomicInteger(0)
            shouldThrow<Exception> {
                post(utilReturning(HttpStatus.INTERNAL_SERVER_ERROR, "일시 오류", calls), retries = 2)
            }
            calls.get() shouldBe 3
        }
    }

    Given("4xx 응답") {
        Then("W-6 분류되지 않고 WebClientResponseException 이 그대로 나온다") {
            // onStatus 가 5xx 만 잡는다. 4xx 는 Retryable/NonRetryable 어느 쪽으로도
            // 분류되지 않는다는 사실을 고정한다 — 바꾸려면 의도적 변경이어야 한다.
            val ex = shouldThrow<Exception> { post(utilReturning(HttpStatus.BAD_REQUEST, "잘못된 요청")) }
            causeChain(ex).any { it is WebClientResponseException } shouldBe true
        }
    }
})