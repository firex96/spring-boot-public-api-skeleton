package com.rex.api.pub.skeleton.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `CacheConfiguration` 의 `redisCacheManager` **빈 배선**을 검증한다.
 *
 * `RedisCacheSerializerFactoryTest`(단위)는 직렬화기의 계약만 본다. 그 직렬화기가
 * 실제 `RedisCacheManager` 에 물려 컨텍스트에 등록되는지는 보지 않는다.
 *
 * **`IntegrationTestConfiguration` 을 import 하지 않는다.** 그 설정은 `@Primary`
 * `ConcurrentMapCacheManager` 로 캐시를 대체하므로, import 하면 검증 대상이 사라진다.
 * 이 테스트가 존재하는 이유가 바로 그 대체 때문에 Redis 캐시 배선이 한 번도
 * 실행되지 않았다는 것이다.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
@ContextConfiguration(initializers = [TestcontainersConfiguration.Initializer::class])
class RedisCacheManagerWiringIntegrationTest(
    private val applicationContext: ApplicationContext,
) : BehaviorSpec({
    Given("Redis 컨테이너가 기동된 애플리케이션 컨텍스트") {
        Then("RedisCacheManager 가 빈으로 등록되어 있다") {
            applicationContext.getBean(RedisCacheManager::class.java).shouldNotBeNull()
        }

        Then("final data class 를 캐시에 넣고 같은 타입·같은 값으로 되읽는다") {
            val cacheManager = applicationContext.getBean(RedisCacheManager::class.java)
            val cache = cacheManager.getCache("cache-wiring-verification").shouldNotBeNull()

            val sample =
                CacheSample(
                    id = 42L,
                    name = "배선 검증",
                    status = CacheSampleStatus.ACTIVE,
                    createdAt = LocalDateTime.of(2026, 8, 26, 10, 30, 0),
                    validFrom = LocalDate.of(2026, 8, 26),
                )
            val key = "wiring-${System.nanoTime()}"

            cache.put(key, sample)
            val restored = cache.get(key)?.get()

            // Kotlin data class 는 final 이라 타입 id 가 붙지 않으면 이 지점에서 깨진다.
            restored.shouldBeInstanceOf<CacheSample>()
            restored shouldBe sample
        }
    }
})