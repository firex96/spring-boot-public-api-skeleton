package com.rex.api.pub.skeleton.service.example

import com.rex.api.pub.skeleton.dto.example.AsyncProxyResultDto
import com.rex.api.pub.skeleton.dto.example.InternalUserRequestDto
import com.rex.api.pub.skeleton.enums.example.RequestStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ExampleAsyncProxyStateStore 의 상태 전이 계약.
 *
 * 워커의 점유(`PENDING → PROCESSING`)와 취소(`PENDING → CANCELLED`)는 다른 스레드에서 경쟁한다 — 읽고-확인하고-쓰기로 하면
 * 성공한 취소가 뒤집혀 외부 호출이 그대로 나간다. [ExampleAsyncProxyStateStore.tryTransition] 은 비교 후 교체 한 번이라 승자가 하나다.
 */
class ExampleAsyncProxyStateStoreTest : BehaviorSpec({
    val store = ExampleAsyncProxyStateStore()

    fun open(userId: String): String =
        store.open(InternalUserRequestDto(userId = userId, userName = "테스트", email = null, phone = null)).requestId

    fun result(
        requestId: String,
        status: RequestStatus,
        success: Boolean,
    ) = AsyncProxyResultDto(requestId = requestId, success = success, status = status)

    Given("PENDING 요청") {
        When("현재 상태와 다른 from 으로 전이를 시도하면") {
            val requestId = open("mismatch")
            val moved = store.tryTransition(requestId, RequestStatus.PROCESSING, RequestStatus.COMPLETED, "완료")

            Then("false 이고 상태는 그대로 PENDING") {
                moved shouldBe false
                store.status(requestId).shouldNotBeNull().status shouldBe RequestStatus.PENDING
            }
        }

        When("PENDING → PROCESSING 점유 뒤 PENDING → CANCELLED 취소를 시도하면") {
            val requestId = open("occupied")
            val occupied = store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.PROCESSING, "처리 중")
            val cancelled = store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.CANCELLED, "취소")

            Then("점유는 true, 취소는 false, 상태는 PROCESSING") {
                occupied shouldBe true
                cancelled shouldBe false
                store.status(requestId).shouldNotBeNull().status shouldBe RequestStatus.PROCESSING
            }
        }

        When("PROCESSING 소유 없이 finish 하면") {
            val requestId = open("no-owner")
            val published = store.finish(requestId, RequestStatus.COMPLETED, "완료", result(requestId, RequestStatus.COMPLETED, true))

            Then("false 이고 결과도 상태도 기록되지 않는다") {
                published shouldBe false
                store.result(requestId) shouldBe null
                store.status(requestId).shouldNotBeNull().status shouldBe RequestStatus.PENDING
            }
        }

        When("실패 publish 뒤 재시도가 점유·완료하고, 첫 실행이 늦게 다시 publish 하면") {
            val requestId = open("late-publish")
            store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.PROCESSING, "처리 중") shouldBe true
            val firstFailed = store.finish(requestId, RequestStatus.FAILED, "실패", result(requestId, RequestStatus.FAILED, false))
            val retried = store.tryTransition(requestId, RequestStatus.FAILED, RequestStatus.PROCESSING, "재시도")
            val retryDone = store.finish(requestId, RequestStatus.COMPLETED, "완료", result(requestId, RequestStatus.COMPLETED, true))
            val latePublish = store.finish(requestId, RequestStatus.FAILED, "늦은 실패", result(requestId, RequestStatus.FAILED, false))

            Then("늦은 publish 는 버려지고 상태·결과는 재시도 실행의 것이다") {
                firstFailed shouldBe true
                retried shouldBe true
                retryDone shouldBe true
                latePublish shouldBe false
                store.status(requestId).shouldNotBeNull().status shouldBe RequestStatus.COMPLETED
                store.result(requestId).shouldNotBeNull().success shouldBe true
            }
        }

        When("실패 publish 와 재시도 점유가 두 스레드에서 동시에 일어나면 (200회 반복)") {
            val executor = Executors.newFixedThreadPool(2)
            val outcomes =
                (1..200).map { round ->
                    val requestId = open("retry-race-$round")
                    store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.PROCESSING, "처리 중") shouldBe true
                    val barrier = CyclicBarrier(2)
                    val publish =
                        executor.submit<Boolean> {
                            barrier.await(5, TimeUnit.SECONDS)
                            store.finish(requestId, RequestStatus.FAILED, "실패", result(requestId, RequestStatus.FAILED, false))
                        }
                    val retry =
                        executor.submit<Boolean> {
                            barrier.await(5, TimeUnit.SECONDS)
                            val occupied = store.tryTransition(requestId, RequestStatus.FAILED, RequestStatus.PROCESSING, "재시도")
                            // 점유에 성공했다면 실패 결과는 이미 기록돼 있어야 한다(결과 저장과 상태 전이가 한 판정 안이라서)
                            occupied && store.result(requestId) != null
                        }
                    Triple(publish.get(5, TimeUnit.SECONDS), retry.get(5, TimeUnit.SECONDS), store.status(requestId).shouldNotBeNull().status)
                }
            executor.shutdown()

            Then("publish 는 항상 성공하고, 재시도가 점유했다면 결과가 먼저 보이며, 최종 상태는 PROCESSING(재시도) 또는 FAILED") {
                outcomes.filter { (published, _, _) -> !published }.shouldContainExactly(emptyList())
                outcomes.filter { (_, _, status) -> status !in setOf(RequestStatus.PROCESSING, RequestStatus.FAILED) }.shouldContainExactly(emptyList())
            }
        }

        When("점유와 취소가 두 스레드에서 동시에 일어나면 (200회 반복)") {
            val executor = Executors.newFixedThreadPool(2)
            val outcomes =
                (1..200).map { round ->
                    val requestId = open("race-$round")
                    val barrier = CyclicBarrier(2)
                    val occupy =
                        executor.submit<Boolean> {
                            barrier.await(5, TimeUnit.SECONDS)
                            store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.PROCESSING, "처리 중")
                        }
                    val cancel =
                        executor.submit<Boolean> {
                            barrier.await(5, TimeUnit.SECONDS)
                            store.tryTransition(requestId, RequestStatus.PENDING, RequestStatus.CANCELLED, "취소")
                        }
                    val occupied = occupy.get(5, TimeUnit.SECONDS)
                    val cancelled = cancel.get(5, TimeUnit.SECONDS)
                    val finalStatus = store.status(requestId).shouldNotBeNull().status
                    Triple(occupied, cancelled, finalStatus)
                }
            executor.shutdown()

            Then("매 회 정확히 하나만 이기고, 최종 상태는 이긴 쪽이다") {
                outcomes.filter { (occupied, cancelled, _) -> occupied == cancelled }.shouldContainExactly(emptyList())
                outcomes.filter { (occupied, _, status) -> occupied && status != RequestStatus.PROCESSING }.shouldContainExactly(emptyList())
                outcomes.filter { (_, cancelled, status) -> cancelled && status != RequestStatus.CANCELLED }.shouldContainExactly(emptyList())
            }
        }
    }
})