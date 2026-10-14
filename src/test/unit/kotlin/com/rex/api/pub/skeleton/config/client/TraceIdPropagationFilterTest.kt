package com.rex.api.pub.skeleton.config.client

import com.rex.api.pub.skeleton.constant.Constant
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import java.net.URI

/**
 * 아웃바운드 traceId 전파 검증. (테스트_시나리오.md T-1 ~ T-6)
 *
 * 네트워크·컨테이너 없이 스텁 [ExchangeFunction] 이 받은 [ClientRequest] 를 직접 단언한다.
 */
class TraceIdPropagationFilterTest : BehaviorSpec({

    lateinit var captured: ClientRequest

    fun stub(): ExchangeFunction =
        ExchangeFunction { request ->
            captured = request
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
        }

    fun request(): ClientRequest = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost/test")).build()

    afterTest { MDC.clear() }

    Given("MDC 에 traceId 가 있을 때") {
        When("아웃바운드 호출을 하면") {
            MDC.put(Constant.KEY_TRACE_ID, "trace-abc-123")
            TraceIdPropagationFilter.create().filter(request(), stub()).block()

            Then("T-1·T-2 헤더가 그 값 그대로 실린다") {
                captured.headers().getFirst(Constant.HEADER_TRACE_ID) shouldBe "trace-abc-123"
            }

            Then("T-5 헤더 이름은 X-Trace-Id 이며 구 하드코딩 'traceId' 가 아니다") {
                Constant.HEADER_TRACE_ID shouldBe "X-Trace-Id"
                captured.headers().getFirst("traceId").shouldBeNull()
            }

            Then("T-6 수신측 TraceIdFilter 가 읽는 상수와 같은 이름이므로 트레이스가 이어진다") {
                // TraceIdFilter 는 Constant.HEADER_TRACE_ID 로 인바운드를 읽는다.
                // 아웃바운드가 같은 상수·같은 값으로 나가야 체인이 이어진다.
                // (MDC 는 afterTest 에서 비워지므로 단언 시점에 읽지 않는다)
                captured.headers().getFirst(Constant.HEADER_TRACE_ID) shouldBe "trace-abc-123"
            }
        }
    }

    Given("TraceIdFilter 가 생성한 UUID 가 MDC 에 있을 때") {
        When("아웃바운드 호출을 하면") {
            val generated = "1e0f9b2c-0000-4000-8000-abcdefabcdef"
            MDC.put(Constant.KEY_TRACE_ID, generated)
            TraceIdPropagationFilter.create().filter(request(), stub()).block()

            Then("T-3 생성된 값이 전파된다") {
                captured.headers().getFirst(Constant.HEADER_TRACE_ID) shouldBe generated
            }
        }
    }

    Given("MDC 에 traceId 가 없을 때 (스케줄러 등 비요청 스레드)") {
        When("아웃바운드 호출을 하면") {
            MDC.clear()
            TraceIdPropagationFilter.create().filter(request(), stub()).block()

            Then("T-4 헤더를 붙이지 않는다") {
                captured.headers().getFirst(Constant.HEADER_TRACE_ID).shouldBeNull()
            }
        }
    }

    Given("MDC 값이 공백일 때") {
        When("아웃바운드 호출을 하면") {
            MDC.put(Constant.KEY_TRACE_ID, "   ")
            TraceIdPropagationFilter.create().filter(request(), stub()).block()

            Then("T-4 빈 헤더를 붙이지 않는다") {
                captured.headers().getFirst(Constant.HEADER_TRACE_ID).shouldBeNull()
            }
        }
    }

    Given("ClientConfiguration 이 만든 WebClient") {
        When("등록된 필터 체인을 그대로 실행하면") {
            val configuration =
                ClientConfiguration(
                    clients = ClientDestinations(ClientDestinations.ClientInfo(url = "http://localhost")),
                    properties =
                    ClientProperties(
                        httpClient = ClientProperties.HttpClientProperties(connectionTimeout = 1000),
                        webClient = ClientProperties.WebClientProperties(memorySize = 1024),
                        connection =
                        ClientProperties.ConnectionPoolProperties(
                            maxConnections = 1,
                            maxIdleTime = 1,
                            maxLifeTime = 1,
                            pendingAcquireTimeout = 1,
                            pendingAcquireMaxCount = 1,
                            evictInBackground = 1,
                        ),
                    ),
                )

            var registered = emptyList<org.springframework.web.reactive.function.client.ExchangeFilterFunction>()
            configuration.webClientBuilder().filters { registered = it.toList() }

            MDC.put(Constant.KEY_TRACE_ID, "wired-999")
            registered.fold(stub()) { next, filter -> filter.apply(next) }.exchange(request()).block()

            Then("필터가 실제로 등록되어 헤더가 실린다") {
                captured.headers().getFirst(Constant.HEADER_TRACE_ID) shouldBe "wired-999"
            }
        }
    }

    Given("호출자가 이미 X-Trace-Id 를 지정한 요청") {
        When("필터를 지나면") {
            MDC.put(Constant.KEY_TRACE_ID, "mdc-wins")
            val preset =
                ClientRequest
                    .create(HttpMethod.GET, URI.create("http://localhost/test"))
                    .header(Constant.HEADER_TRACE_ID, "caller-supplied")
                    .build()
            TraceIdPropagationFilter.create().filter(preset, stub()).block()

            Then("헤더가 단일 값으로 교체된다 (중복 전송 없음)") {
                captured.headers()[Constant.HEADER_TRACE_ID]?.size shouldBe 1
                captured.headers().getFirst(Constant.HEADER_TRACE_ID) shouldBe "mdc-wins"
            }
        }
    }

    Given("MDC 에 형식이 어긋난 traceId 가 들어 있을 때") {
        When("아웃바운드 호출을 하면") {
            MDC.put(Constant.KEY_TRACE_ID, "bad\nvalue")
            TraceIdPropagationFilter.create().filter(request(), stub()).block()

            Then("전파하지 않는다 — 오염된 값이 하위 서비스로 번지지 않는다") {
                captured.headers().getFirst(Constant.HEADER_TRACE_ID).shouldBeNull()
            }
        }
    }
})