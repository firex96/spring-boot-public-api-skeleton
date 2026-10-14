package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.context.AuditorContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 감사 주체가 비동기 스레드로 옳게 넘어가는지.
 *
 * **워커를 하나로 고정한다**(`corePoolSize = 1`) — 재사용을 강제해야
 * 앞 작업의 값이 남는지 볼 수 있다.
 *
 * **N-2b 가 이 슬라이스의 안전장치다.** 워커에 값이 남으면 컨텍스트 없이 제출한
 * 작업이 남의 ID 로 감사되고, 그것은 로그가 아니라 **DB 에 남는다** — 이 작업이
 * 고치러 온 문제를 새 채널에 다시 만드는 셈이다.
 */
class AuditorContextTaskDecoratorTest : BehaviorSpec({

    afterTest { AuditorContext.clear() }

    fun singleWorkerExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            setTaskDecorator(AuditorContextTaskDecorator())
            initialize()
        }

    Given("감사 주체 42 로 작업을 제출할 때") {
        Then("N-2a 작업 안에서 42 가 보인다") {
            val executor = singleWorkerExecutor()
            var seen: Long? = null
            val done = CountDownLatch(1)
            try {
                AuditorContext.set(42L)
                executor.execute {
                    seen = AuditorContext.get()
                    done.countDown()
                }
                done.await(5, TimeUnit.SECONDS) shouldBe true
            } finally {
                executor.shutdown()
            }

            seen shouldBe 42L
        }
    }

    Given("42 로 제출한 뒤 같은 워커에 컨텍스트 없이 제출할 때") {
        // 워커가 재사용된다. 앞 작업의 값이 남으면 여기서 42 가 보인다.
        Then("N-2b 두 번째 작업은 비어 있다") {
            val executor = singleWorkerExecutor()
            var second: Long? = 999
            val first = CountDownLatch(1)
            val done = CountDownLatch(1)
            try {
                AuditorContext.set(42L)
                executor.execute { first.countDown() }
                first.await(5, TimeUnit.SECONDS) shouldBe true

                AuditorContext.clear()
                executor.execute {
                    second = AuditorContext.get()
                    done.countDown()
                }
                done.await(5, TimeUnit.SECONDS) shouldBe true
            } finally {
                executor.shutdown()
            }

            second.shouldBeNull()
        }
    }

    Given("작업이 예외를 던질 때") {
        Then("N-2c 예외가 전파돼도 워커가 정리된다") {
            AuditorContext.set(42L)
            val decorated =
                AuditorContextTaskDecorator().decorate {
                    error("작업 실패")
                }

            shouldThrow<IllegalStateException> { decorated.run() }

            // 데코레이터가 같은 스레드에서 돌았으므로 여기서 바로 확인된다.
            AuditorContext.get().shouldBeNull()
        }
    }
})