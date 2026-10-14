package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.client.example.ExampleWebClient
import com.rex.api.pub.skeleton.config.AsyncConfiguration
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.InternalUserResponseDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * `ExampleAsyncProxyBizService.submitRequest` 가 **실제로 비동기**인지 Spring 컨텍스트에서 증명한다.
 *
 * 유닛 테스트(mockk 워커·프록시 없는 워커)는 `@EnableAsync` 배선이 깨져도 통과한다. 여기서는 `AsyncConfiguration` 과 빈 3개만 올린
 * 컨텍스트(Docker 불필요)에서 외부 호출을 래치로 붙잡아 두고, 접수가 1초 안에 돌아오며 상태가 PENDING/PROCESSING 인 것 →
 * 래치를 풀면 COMPLETED 로 수렴하는 것을 본다. 워커의 `@Async` 를 떼면 접수가 래치에 막혀 실패한다.
 */
@SpringBootTest(
    classes = [
        AsyncConfiguration::class,
        ExampleAsyncProxyBizService::class,
        ExampleAsyncProxyWorker::class,
        ExampleAsyncProxyStateStore::class,
        ExampleAsyncProxyAsyncExecutionIntegrationTest.LatchFixture::class,
    ],
)
class ExampleAsyncProxyAsyncExecutionIntegrationTest(
    private val service: ExampleAsyncProxyBizService,
    private val fixture: LatchFixture,
) : BehaviorSpec({
    Given("외부 호출이 래치에 막혀 있는 비동기 프록시 예제") {
        val request = InternalUserRequestDto(userId = "async-proof", userName = "비동기 증명", email = null, phone = null)

        When("요청을 접수하면") {
            val startedAt = System.nanoTime()
            val requestId = service.submitRequest(request)
            val elapsedNanos = System.nanoTime() - startedAt

            Then("외부 호출이 끝나기 전에 1초 안에 돌아오고 상태는 PENDING 또는 PROCESSING 이다") {
                elapsedNanos shouldBeLessThan ONE_SECOND_NANOS
                service.getRequestStatus(requestId).shouldNotBeNull().status shouldBeIn
                    listOf(RequestStatus.PENDING, RequestStatus.PROCESSING)
            }

            Then("래치를 풀면 백그라운드 스레드가 COMPLETED 로 마무리한다") {
                fixture.latch.countDown()
                eventually(5.seconds) {
                    service.getRequestStatus(requestId).shouldNotBeNull().status shouldBe RequestStatus.COMPLETED
                }
                service.getResult(requestId).shouldNotBeNull().success shouldBe true
            }
        }
    }
}) {
    @TestConfiguration
    class LatchFixture {
        val latch = CountDownLatch(1)

        @Bean
        fun exampleWebClient(): ExampleWebClient =
            mockk {
                every { registerUser(any()) } answers {
                    latch.await(10, TimeUnit.SECONDS)
                    InternalUserResponseDto(success = true, message = "등록 성공", user = null)
                }
            }
    }

    companion object {
        private const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}