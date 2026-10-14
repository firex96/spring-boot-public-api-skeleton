package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.dto.example.AsyncProxyResultDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.mockk
import io.mockk.verify

/**
 * ExampleAsyncProxyBizService 단위 테스트.
 *
 * 워커는 mockk — 서비스의 계약은 「접수 즉시 PENDING 으로 등록하고 워커에 한 번 위임한 뒤 requestId 를 돌려준다」이다.
 * 처리 결과(COMPLETED·FAILED·EXHAUSTED)는 `ExampleAsyncProxyWorkerTest`, 실제 비동기 실행은
 * `ExampleAsyncProxyAsyncExecutionIntegrationTest` 가 본다.
 */
class ExampleAsyncProxyBizServiceTest : BehaviorSpec({
    val store = ExampleAsyncProxyStateStore()
    val worker = mockk<ExampleAsyncProxyWorker>(relaxed = true)
    val asyncProxyService = ExampleAsyncProxyBizService(store, worker)

    Given("비동기 요청 접수") {
        val request =
            InternalUserRequestDto(
                userId = "user123",
                userName = "홍길동",
                email = "hong@example.com",
                phone = "010-1234-5678",
            )

        When("요청을 접수하면") {
            val requestId = asyncProxyService.submitRequest(request)

            Then("requestId가 반환됨") {
                requestId.shouldNotBeEmpty()
            }

            Then("상태가 PENDING 으로 등록됨 — 워커가 돌기 전이라 처리 결과는 없다") {
                val status = asyncProxyService.getRequestStatus(requestId)
                status.shouldNotBeNull()
                status.userId shouldBe "user123"
                status.status shouldBe RequestStatus.PENDING
                asyncProxyService.getResult(requestId).shouldBeNull()
            }

            Then("워커에 정확히 한 번 위임함") {
                verify(exactly = 1) { worker.process(requestId, request) }
            }
        }
    }

    Given("상태 조회") {
        val request =
            InternalUserRequestDto(
                userId = "user456",
                userName = "김철수",
                email = null,
                phone = null,
            )

        When("존재하지 않는 requestId로 조회하면") {
            val status = asyncProxyService.getRequestStatus("non-existent-id")

            Then("null 반환") {
                status.shouldBeNull()
            }
        }

        When("존재하는 requestId로 조회하면") {
            val requestId = asyncProxyService.submitRequest(request)
            val status = asyncProxyService.getRequestStatus(requestId)

            Then("상태 정보가 반환됨") {
                status.shouldNotBeNull()
                status.userId shouldBe "user456"
            }
        }
    }

    Given("요청 취소") {
        When("존재하지 않는 requestId를 취소하면") {
            val cancelResult = asyncProxyService.cancelRequest("non-existent-id")

            Then("false 반환") {
                cancelResult shouldBe false
            }
        }

        When("대기 중인 요청을 취소하면") {
            val request =
                InternalUserRequestDto(
                    userId = "user-pending",
                    userName = "대기테스트",
                    email = null,
                    phone = null,
                )
            val requestId = asyncProxyService.submitRequest(request)
            val cancelResult = asyncProxyService.cancelRequest(requestId)

            Then("true 반환하고 상태가 CANCELLED 로 바뀜") {
                cancelResult shouldBe true
                asyncProxyService.getRequestStatus(requestId)?.status shouldBe RequestStatus.CANCELLED
            }
        }

        When("워커가 이미 점유한(PROCESSING) 요청을 취소하면") {
            val request =
                InternalUserRequestDto(
                    userId = "user-processing",
                    userName = "점유테스트",
                    email = null,
                    phone = null,
                )
            val requestId = asyncProxyService.submitRequest(request)
            store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.PROCESSING, "처리 중입니다.")

            val cancelResult = asyncProxyService.cancelRequest(requestId)

            Then("false 반환 — 점유와 취소는 비교 후 교체로 하나만 이긴다") {
                cancelResult shouldBe false
                asyncProxyService.getRequestStatus(requestId)?.status shouldBe RequestStatus.PROCESSING
            }
        }

        When("이미 처리 완료된 요청을 취소하면") {
            val request =
                InternalUserRequestDto(
                    userId = "user-completed",
                    userName = "완료테스트",
                    email = null,
                    phone = null,
                )
            val requestId = asyncProxyService.submitRequest(request)
            store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.PROCESSING, "처리 중입니다.")
            store.finish(
                requestId,
                RequestStatus.COMPLETED,
                "처리가 완료되었습니다.",
                AsyncProxyResultDto(requestId = requestId, success = true, status = RequestStatus.COMPLETED),
            )

            val cancelResult = asyncProxyService.cancelRequest(requestId)

            Then("false 반환 (처리 완료 후에는 취소 불가)") {
                cancelResult shouldBe false
            }
        }
    }

    Given("결과 조회") {
        When("존재하지 않는 requestId로 결과를 조회하면") {
            val result = asyncProxyService.getResult("non-existent-id")

            Then("null 반환") {
                result.shouldBeNull()
            }
        }
    }
})