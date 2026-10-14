package com.rex.api.pub.skeleton.config

import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.kotlinModule

/**
 * Redis 캐시 값 직렬화기.
 *
 * CacheConfiguration 안에 인라인으로 두면 테스트가 이 계약을 지나가지 못하므로 분리한다
 * (테스트 설정이 ConcurrentMapCacheManager 를 @Primary 로 올린다).
 *
 * 역직렬화 허용 서브타입을 최소 화이트리스트로 제한한다 — 무제한 default typing 은 캐시가 오염됐을 때
 * @class 힌트로 임의 타입을 인스턴스화할 수 있는 gadget 경로가 된다.
 *
 * 패키지 접두사를 넓게 잡는 것으로는 부족하다: java.util 전체를 허용하면
 * java.util.logging.SocketHandler 처럼 무인자 생성자로 네트워크 연결을 여는 타입이 포함된다.
 * 이 스켈레톤을 복제한 프로젝트는 자기 캐시가 담는 타입만 이 목록에 추가해야 한다.
 */
object RedisCacheSerializerFactory {
    fun create(): RedisSerializer<Any> {
        val polymorphicTypeValidator =
            BasicPolymorphicTypeValidator
                .builder()
                .allowIfSubType("com.rex.api.pub.skeleton.")
                // 스칼라 값 타입 — 현재는 final 이라 타입 id 가 붙지 않아 이 항목 없이도 왕복한다
                // (테스트로 확인됨). 타이핑 정책이 바뀌어도 스칼라 캐싱이 조용히 깨지지 않도록 명시한다.
                .allowIfSubType("java.lang.String")
                .allowIfSubType("java.lang.Number")
                .allowIfSubType("java.lang.Boolean")
                .allowIfSubType("java.lang.Character")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.util.LinkedHashMap")
                .allowIfSubType("java.util.LinkedHashSet")
                .allowIfSubType("java.util.HashMap")
                .allowIfSubType("java.util.HashSet")
                .allowIfSubType("org.springframework.cache.support.NullValue")
                .build()

        return GenericJacksonJsonRedisSerializer
            .builder()
            .enableDefaultTyping(polymorphicTypeValidator)
            .enableSpringCacheNullValueSupport()
            .customize { builder ->
                builder
                    .addModule(kotlinModule())
                    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }.build()
    }
}