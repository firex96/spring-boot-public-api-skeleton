package com.rex.api.pub.skeleton.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.cache.support.NullValue
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Redis 캐시 직렬화 계약 검증. (테스트_시나리오.md J-1 ~ J-3 + enum 왕복)
 *
 * Redis 서버·컨테이너 없이 serialize/deserialize 왕복만으로 판정한다.
 */
class RedisCacheSerializerFactoryTest : BehaviorSpec({

    val serializer = RedisCacheSerializerFactory.create()

    val sample =
        CacheSample(
            id = 42L,
            name = "홍길동",
            status = CacheSampleStatus.ACTIVE,
            createdAt = LocalDateTime.of(2026, 8, 26, 13, 45, 30),
            validFrom = LocalDate.of(2026, 1, 1),
        )

    Given("캐시 값을 직렬화하면") {
        val bytes = serializer.serialize(sample)!!
        val json = String(bytes)

        When("다시 역직렬화하면") {
            val restored = serializer.deserialize(bytes)

            Then("J-1 저장 전 객체와 동등하다") {
                restored shouldBe sample
            }

            Then("enum 필드가 값 그대로 복원된다") {
                (restored as CacheSample).status shouldBe CacheSampleStatus.ACTIVE
            }
        }

        Then("J-2 날짜가 ISO 문자열로 직렬화된다 (타임스탬프 숫자 아님)") {
            json shouldContain "2026-08-26T13:45:30"
            json shouldContain "2026-01-01"
            json shouldNotContain "1787"
        }

        Then("타입 정보가 포함된다 (기본 타이핑 활성)") {
            json shouldContain CacheSample::class.java.name
        }
    }

    Given("알 수 없는 속성이 포함된 JSON") {
        val bytes = serializer.serialize(sample)!!
        val polluted =
            String(bytes).replaceFirst(
                "\"name\"",
                "\"unknownFieldAddedLater\":\"ignore-me\",\"name\"",
            )

        When("역직렬화하면") {
            val restored = serializer.deserialize(polluted.toByteArray())

            Then("J-3 실패하지 않고 알려진 필드가 복원된다") {
                (restored as CacheSample).id shouldBe 42L
                restored.name shouldBe "홍길동"
            }
        }
    }

    Given("Spring 캐시의 null 값 (NullValue)") {
        When("왕복시키면") {
            val bytes = serializer.serialize(NullValue.INSTANCE)!!
            val restored = serializer.deserialize(bytes)

            Then("예외 없이 NullValue 로 복원된다 — @Cacheable 이 null 을 반환하는 정상 경로") {
                // enableSpringCacheNullValueSupport() 가 없으면 이 왕복이 깨진다.
                // 역직렬화된 것은 새 인스턴스이므로 동일성이 아니라 타입으로 판정한다
                // (NullValue.equals 는 자기 자신 또는 null 에만 true).
                restored!!.javaClass shouldBe NullValue::class.java
            }
        }
    }

    Given("허용 목록 밖 타입을 가리키는 오염된 JSON") {
        // 거부 "사유" 까지 단언한다. 예외가 났다는 사실만으로는 타입 검증기가 막았다는 증명이 못 된다
        // (JSON 형식 오류로도 예외는 난다).
        // 타입 id 표기 형식은 대상 타입에 따라 다르다(@class 프로퍼티 / WRAPPER_ARRAY).
        // 형식이 안 맞으면 검증기 이전에 파싱이 실패해 "거부됐다" 를 증명하지 못하므로 형식을 맞춰 준다.
        val hostilePayloads =
            listOf(
                "java.util.logging.SocketHandler" to """{"@class":"java.util.logging.SocketHandler","x":1}""",
                "java.net.Socket" to """{"@class":"java.net.Socket","x":1}""",
                "java.io.File" to """["java.io.File","/tmp/x"]""",
            )

        hostilePayloads.forEach { (fqcn, hostile) ->
            When("타입 id 가 $fqcn 인 값을 역직렬화하면") {
                val thrown = shouldThrow<Exception> { serializer.deserialize(hostile.toByteArray()) }

                Then("PolymorphicTypeValidator 가 거부한다") {
                    thrown.message shouldContain "PolymorphicTypeValidator"
                    thrown.message shouldContain fqcn
                }
            }
        }
    }

    Given("기본 타입을 반환하는 @Cacheable (String / Long / Int / Boolean)") {
        val cases = listOf<Any>("문자열 값", 42L, 7, true)

        cases.forEach { value ->
            When("${value::class.simpleName} 을 왕복시키면") {
                val bytes = serializer.serialize(value)!!
                val restored = serializer.deserialize(bytes)

                Then("원본과 동등하다 — 화이트리스트가 기본 타입 캐싱을 깨뜨리지 않는다") {
                    restored shouldBe value
                }
            }
        }
    }
})