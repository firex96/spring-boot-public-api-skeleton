package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.PublicApiSkeletonApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.SpringApplication

/**
 * 가드가 **실제 기동 경로**에서 동작하는지 검증한다.
 *
 * 유닛 테스트(`UnresolvedPlaceholderGuardTest`)는 가드 메서드를 직접 부르므로 `META-INF/spring.factories` 등록이
 * 빠지거나 무시돼도 통과한다. 이 테스트는 `SpringApplication.run` 이 spring.factories 의 `ApplicationListener` 를
 * 로드하고 `ApplicationEnvironmentPreparedEvent` 를 발행하는 경로를 그대로 탄다. 가드는 그 시점에 던지므로
 * 컨텍스트 refresh(데이터소스·Testcontainers)에는 도달하지 않는다 — Docker 없이도 돈다.
 */
class UnresolvedPlaceholderGuardBootIntegrationTest : BehaviorSpec({
    Given("해석되지 않는 논리 키 참조를 가진 프로파일 yml(application-guardprobe.yml)로 기동할 때") {
        Then("G-7 spring.factories 로 등록된 가드가 환경 준비 단계에서 IllegalStateException 으로 기동을 막는다") {
            val exception =
                shouldThrow<IllegalStateException> {
                    SpringApplication(PublicApiSkeletonApplication::class.java).run(
                        "--spring.profiles.active=testcontainers,guardprobe",
                    )
                }
            exception.message shouldContain "해석되지 않은 설정 플레이스홀더"
            exception.message shouldContain "guard.probe.secret -> \${no-such-secret-for-guard-probe}"
        }
    }
})