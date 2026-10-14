package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.client.example.ExampleWebClient
import com.rex.api.pub.skeleton.dto.example.InternalUserDataDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.dto.example.InternalUserResponseDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import com.rex.api.pub.skeleton.enums.example.UserStatus
import com.rex.api.pub.skeleton.exception.ApiCallException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime

/**
 * ExampleAsyncProxyWorker 단위 테스트.
 *
 * 프록시 없는 실제 인스턴스라 `@Async` 는 붙어 있어도 동기로 돈다 — 처리 결과(상태 전이·결과 저장·재시도 횟수)만 본다.
 */
class ExampleAsyncProxyWorkerTest : BehaviorSpec({
    val exampleWebClient = mockk<ExampleWebClient>()
    val store = ExampleAsyncProxyStateStore()
    val worker = ExampleAsyncProxyWorker(exampleWebClient = exampleWebClient, store = store)

    fun open(userId: String): String =
        store.open(InternalUserRequestDto(userId = userId, userName = "테스트", email = null, phone = null)).requestId

    Given("백그라운드 처리") {
        val request =
            InternalUserRequestDto(
                userId = "user-async",
                userName = "비동기테스트",
                email = "async@test.com",
                phone = null,
            )

        When("외부 API가 성공하면") {
            every { exampleWebClient.registerUser(any()) } returns
                InternalUserResponseDto(
                    success = true,
                    message = "등록 성공",
                    user =
                    InternalUserDataDto(
                        userId = "user-async",
                        registeredAt = LocalDateTime.now(),
                        status = UserStatus.ACTIVE,
                    ),
                )

            val requestId = open("user-async")
            worker.process(requestId, request)

            Then("상태가 COMPLETED로 변경됨") {
                val status = store.status(requestId)
                status.shouldNotBeNull()
                status.status shouldBe RequestStatus.COMPLETED
                status.completedAt.shouldNotBeNull()
            }

            Then("결과가 저장됨") {
                val result = store.result(requestId)
                result.shouldNotBeNull()
                result.success shouldBe true
                result.response?.user?.userId shouldBe "user-async"
            }
        }

        When("외부 API가 비즈니스 실패를 반환하면") {
            every { exampleWebClient.registerUser(any()) } returns
                InternalUserResponseDto(
                    success = false,
                    message = "사용자 이미 존재",
                    user = null,
                )

            val requestId = open("user-fail")
            worker.process(requestId, request)

            Then("상태가 FAILED로 변경됨") {
                val status = store.status(requestId)
                status.shouldNotBeNull()
                status.status shouldBe RequestStatus.FAILED
            }

            Then("실패 결과가 저장됨") {
                val result = store.result(requestId)
                result.shouldNotBeNull()
                result.success shouldBe false
                result.response?.message shouldBe "사용자 이미 존재"
            }
        }

        When("외부 API 호출 중 예외가 발생하면") {
            every { exampleWebClient.registerUser(any()) } throws ApiCallException("Connection refused")

            val requestId = open("user-error")
            worker.process(requestId, request)

            Then("상태가 FAILED로 변경되고 재시도 횟수가 1") {
                val status = store.status(requestId)
                status.shouldNotBeNull()
                status.status shouldBe RequestStatus.FAILED
                status.retryCount shouldBe 1
            }

            Then("에러 결과가 저장됨") {
                val result = store.result(requestId)
                result.shouldNotBeNull()
                result.success shouldBe false
            }
        }

        When("예외가 세 번 반복되면") {
            every { exampleWebClient.registerUser(any()) } throws ApiCallException("Connection refused")

            val requestId = open("user-exhausted")
            repeat(3) { worker.process(requestId, request) }

            Then("상태가 EXHAUSTED 로 끝나고 재시도 횟수가 3") {
                val status = store.status(requestId)
                status.shouldNotBeNull()
                status.status shouldBe RequestStatus.EXHAUSTED
                status.retryCount shouldBe 3
                store.result(requestId)?.status shouldBe RequestStatus.EXHAUSTED
            }
        }

        When("실패한 실행 뒤 재시도가 성공하면") {
            val retryWebClient = mockk<ExampleWebClient>()
            val retryWorker = ExampleAsyncProxyWorker(exampleWebClient = retryWebClient, store = store)
            every { retryWebClient.registerUser(any()) } throws ApiCallException("first") andThen
                InternalUserResponseDto(success = true, message = "재시도 성공", user = null)

            val requestId = open("user-retry")
            retryWorker.process(requestId, request)
            retryWorker.process(requestId, request)

            Then("상태 COMPLETED 와 결과 success 가 같은 실행의 것이다 — 이전 실패 결과가 남지 않는다") {
                store.status(requestId).shouldNotBeNull().status shouldBe RequestStatus.COMPLETED
                val result = store.result(requestId).shouldNotBeNull()
                result.success shouldBe true
                result.status shouldBe RequestStatus.COMPLETED
                result.response?.message shouldBe "재시도 성공"
            }
        }

        When("이미 취소된 요청이면") {
            val cancelledWebClient = mockk<ExampleWebClient>()
            val cancelledWorker = ExampleAsyncProxyWorker(exampleWebClient = cancelledWebClient, store = store)
            val requestId = open("user-cancelled")
            store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.CANCELLED, "요청이 취소되었습니다.")

            cancelledWorker.process(requestId, request)

            Then("외부 API 를 부르지 않고 상태도 그대로 CANCELLED") {
                verify(exactly = 0) { cancelledWebClient.registerUser(any()) }
                store.status(requestId)?.status shouldBe RequestStatus.CANCELLED
            }
        }
    }
})