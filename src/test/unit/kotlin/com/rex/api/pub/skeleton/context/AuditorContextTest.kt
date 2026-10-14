package com.rex.api.pub.skeleton.context

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * 감사 주체 채널의 수명 계약.
 *
 * **N-1c 가 데코레이터의 전제다** — `set(null)` 이 지우지 않으면 컨텍스트 없이
 * 제출한 비동기 작업이 앞 작업의 사용자 ID 를 물려받고, 그것은 DB 에 남는다.
 */
class AuditorContextTest : BehaviorSpec({

    afterTest { AuditorContext.clear() }

    Given("감사 주체를 세웠을 때") {
        Then("N-1a 그 값을 그대로 돌려준다") {
            AuditorContext.set(42L)

            AuditorContext.get() shouldBe 42L
        }
    }

    Given("세운 뒤 지웠을 때") {
        Then("N-1b 비어 있다") {
            AuditorContext.set(42L)
            AuditorContext.clear()

            AuditorContext.get().shouldBeNull()
        }
    }

    Given("값이 있는 상태") {
        // set(null) 이 덮어쓰기가 아니라 제거여야 한다.
        Then("N-1c null 을 세우면 지워진다 — 앞 값이 남지 않는다") {
            AuditorContext.set(42L)
            AuditorContext.set(null)

            AuditorContext.get().shouldBeNull()
        }
    }
})