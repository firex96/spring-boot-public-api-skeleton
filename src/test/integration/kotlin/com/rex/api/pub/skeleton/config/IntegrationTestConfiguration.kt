package com.rex.api.pub.skeleton.config

import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.reactive.function.client.WebClient

/**
 * 통합 테스트용 설정.
 *
 * 테스트 환경에서 필요한 공통 빈들을 정의합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @SpringBootTest
 * @Import(IntegrationTestConfiguration::class)
 * class MyIntegrationTest {
 *     // ...
 * }
 * ```
 */
@TestConfiguration
class IntegrationTestConfiguration {
    /**
     * 테스트용 인메모리 캐시 매니저.
     *
     * Redis 대신 ConcurrentMapCache를 사용하여 외부 의존성 없이 테스트합니다.
     */
    @Bean
    @Primary
    fun testCacheManager(): CacheManager = ConcurrentMapCacheManager()

    /**
     * 테스트용 WebClient.
     *
     * Social OAuth2 서비스 등에서 사용되는 WebClient.
     */
    @Bean
    @Primary
    fun testWebClient(): WebClient = WebClient.builder().build()

    /**
     * 테스트용 Mock RedisConnectionFactory.
     *
     * Redis 없이 테스트하기 위한 Mock 빈.
     *
     * `RedisConnectionFactory` 는 `PersistenceExceptionTranslator` 이기도 하다 — Spring Data 리포지토리 프록시의
     * `PersistenceExceptionTranslationInterceptor` 가 컨텍스트의 모든 translator 에 예외를 돌리므로, relaxed mock 이
     * `translateExceptionIfPossible` 에 **mock `DataAccessException`** 을 돌려주면 리포지토리에서 난 진짜 예외가 mock 예외로
     * 바뀐다(메시지·원인 소실). 그 mock 의 `cause`·`suppressed` 는 relaxed 라 끝없이 새 mock 을 만들어 JUnit 의
     * `findNestedThrowables` 가 힙을 다 채운다(예외 한 번에 워커 OOM). translator 로는 「모른다(null)」를 돌려준다.
     */
    @Bean
    @Primary
    fun testRedisConnectionFactory(): RedisConnectionFactory =
        mockk(relaxed = true) {
            every { translateExceptionIfPossible(any()) } returns null
        }

    /**
     * 테스트용 Mock StringRedisTemplate.
     *
     * Redis를 사용하는 서비스에서 필요한 템플릿 (Mock).
     */
    @Bean
    @Primary
    fun testStringRedisTemplate(): StringRedisTemplate = mockk(relaxed = true)
}