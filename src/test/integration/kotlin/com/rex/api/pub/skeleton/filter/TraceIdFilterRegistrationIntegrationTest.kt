package com.rex.api.pub.skeleton.filter

import com.rex.api.pub.skeleton.config.IntegrationTestConfiguration
import com.rex.api.pub.skeleton.config.TestcontainersConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

/**
 * `TraceIdFilter` 가 실제 Spring 컨텍스트에 빈으로 등록되는지 검증한다.
 *
 * 단위 테스트는 패키지 불변식만 지키므로 등록 자체는 컨텍스트로 확인한다.
 * 과거 이 필터는 선언 패키지가 scan root 밖이어서 `@Component` 임에도 등록되지 않았다.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
@Import(IntegrationTestConfiguration::class)
class TraceIdFilterRegistrationIntegrationTest(
    private val applicationContext: ApplicationContext,
) : BehaviorSpec({
    Given("기동된 애플리케이션 컨텍스트") {
        Then("TraceIdFilter 가 빈으로 등록되어 있다") {
            applicationContext.getBean(TraceIdFilter::class.java).shouldNotBeNull()
        }
    }
})