package com.rex.api.pub.skeleton.annotation

import org.springframework.cache.annotation.Cacheable
import org.springframework.core.annotation.AliasFor

/**
 * Null 값을 캐싱하지 않는 @Cacheable 합성 어노테이션.
 *
 * 단건 조회에서 결과가 null일 경우 캐시하지 않고,
 * 데이터가 있을 때만 캐싱합니다.
 *
 * 내부적으로 @Cacheable(unless = "#result == null")과 동일하게 동작합니다.
 *
 * 사용 예시:
 * ```kotlin
 * @CacheableNullSafe(value = ["productCache"], key = "#productId")
 * fun findProductById(productId: Long): ProductDto?
 * ```
 *
 * > ⚠️ redisCacheManager의 disableCachingNullValues()와 조합하여 사용 여부를 고려해야 합니다.
 *
 * @param value 캐시 이름 (cacheNames의 별칭)
 * @param cacheNames 캐시 이름
 * @param key SpEL 표현식으로 캐시 키 지정
 * @param condition 캐싱 조건 (기본값: 모든 경우 캐싱)
 * @param cacheManager 사용할 캐시 매니저 이름
 * @param sync 동기화 모드 (기본값: false)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Cacheable(unless = "#result == null")
annotation class CacheableNullSafe(
    @get:AliasFor(annotation = Cacheable::class)
    val value: Array<String> = [],
    @get:AliasFor(annotation = Cacheable::class)
    val cacheNames: Array<String> = [],
    @get:AliasFor(annotation = Cacheable::class)
    val key: String = "",
    @get:AliasFor(annotation = Cacheable::class)
    val condition: String = "",
    @get:AliasFor(annotation = Cacheable::class)
    val cacheManager: String = "",
    @get:AliasFor(annotation = Cacheable::class)
    val sync: Boolean = false,
)