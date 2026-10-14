package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.slf4j.MDC
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * **데코레이터가 옳게 동작하는 것과 그것이 배선된 것은 다른 문제다.**
 * [AuditorContextTaskDecoratorTest] 는 앞을, 이 스펙은 뒤를 본다.
 *
 * 로깅(MDC)과 감사 주체는 채널이 다르므로 둘을 합성해 배선한다.
 * 하나만 걸리면 그 축이 조용히 전파되지 않는다.

 */
class AsyncConfigurationTest : BehaviorSpec({

    afterTest {
        AuditorContext.clear()
        MDC.clear()
    }

    /**
     * 제출 시점의 두 축을 세우고, 작업 안에서 둘 다 보이는지 본다.
     *
     * 이 레포의 `AsyncConfiguration` 은 `initialize()` 를 부르지 않는다(Spring 컨테이너가
     * lifecycle 로 초기화한다). 직접 호출할 때는 테스트가 초기화한다.
     */
    fun observe(executor: ThreadPoolTaskExecutor): Pair<String?, Long?> {
        executor.initialize()
        var trace: String? = null
        var auditor: Long? = null
        val done = CountDownLatch(1)
        try {
            MDC.put(Constant.KEY_TRACE_ID, "wiring-check")
            AuditorContext.set(42L)
            executor.execute {
                trace = MDC.get(Constant.KEY_TRACE_ID)
                auditor = AuditorContext.get()
                done.countDown()
            }
            done.await(5, TimeUnit.SECONDS) shouldBe true
        } finally {
            executor.shutdown()
        }
        return trace to auditor
    }

    Given("taskExecutor() 로 제출한 작업") {
        Then("N-2d MDC 와 감사 주체가 둘 다 전파된다") {
            val (trace, auditor) = observe(AsyncConfiguration().taskExecutor() as ThreadPoolTaskExecutor)

            trace shouldBe "wiring-check"
            auditor shouldBe 42L
        }
    }
})