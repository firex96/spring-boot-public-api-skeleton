package com.rex.api.pub.skeleton.filter

import com.rex.api.pub.skeleton.PublicApiSkeletonApplication
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldStartWith

/**
 * `TraceIdFilter` 가 component scan 도달 범위 안에 있는지 지킨다.
 *
 * 과거 이 필터는 선언 패키지가 애플리케이션 패키지와 달라
 * scan root 밖이었고, `@Component` 임에도 **빈으로 등록되지 않아 인바운드 traceId 처리가
 * 전혀 동작하지 않았다.** 파일 위치만으로는 잡히지 않는 결함이라 불변식으로 고정한다.
 */
class TraceIdFilterScanReachabilityTest : BehaviorSpec({
    Given("애플리케이션 scan root") {
        val scanRoot = PublicApiSkeletonApplication::class.java.`package`.name

        Then("TraceIdFilter 의 선언 패키지가 그 하위여야 한다") {
            TraceIdFilter::class.java.`package`.name shouldStartWith scanRoot
        }

        Then("TraceIdSanitizer 도 마찬가지다") {
            TraceIdSanitizer::class.java.`package`.name shouldStartWith scanRoot
        }
    }
})