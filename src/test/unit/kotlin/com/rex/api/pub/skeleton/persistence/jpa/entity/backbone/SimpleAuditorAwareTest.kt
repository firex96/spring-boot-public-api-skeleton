package com.rex.api.pub.skeleton.persistence.jpa.entity.backbone

import com.rex.api.pub.skeleton.constant.Constant
import com.rex.api.pub.skeleton.context.AuditorContext
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.slf4j.MDC

/**
 * 감사 주체가 **[AuditorContext] 만** 읽는지.
 *
 * **N-5c 가 이 작업의 목표를 직접 관측한다** — MDC 에 `userId` 를 넣어도 감사가
 * 바뀌지 않아야 「MDC 는 로깅 전용」이 성립한다. 이전에는 MDC 가 곧 감사 주체였다.
 */
class SimpleAuditorAwareTest : BehaviorSpec({

    afterTest {
        AuditorContext.clear()
        MDC.clear()
    }

    val auditorAware = SimpleAuditorAware()

    Given("감사 주체가 세워져 있을 때") {
        Then("N-5a 그 값을 감사 주체로 준다") {
            AuditorContext.set(42L)

            auditorAware.getCurrentAuditor().orElse(null) shouldBe 42L
        }
    }

    Given("감사 주체가 비어 있을 때") {
        Then("N-5b 시스템 사용자가 된다") {
            auditorAware.getCurrentAuditor().orElse(null) shouldBe Constant.SYSTEM_USER_ID
        }
    }

    Given("감사 주체는 비어 있고 MDC 에만 userId 가 있을 때") {
        // 채널이 끊겼음을 본다. 예전에는 이 값이 그대로 감사 주체였다.
        Then("N-5c MDC 는 감사에 닿지 않는다 — 시스템 사용자가 된다") {
            MDC.put(Constant.KEY_USER_ID, "42")

            auditorAware.getCurrentAuditor().orElse(null) shouldBe Constant.SYSTEM_USER_ID
        }
    }
})