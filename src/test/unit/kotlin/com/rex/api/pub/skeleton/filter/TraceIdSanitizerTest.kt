package com.rex.api.pub.skeleton.filter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

/**
 * 인바운드 traceId 검증. 외부 입력이 로그·하위 호출 trace 를 오염시키지 못하게 한다.
 */
class TraceIdSanitizerTest : BehaviorSpec({

    Given("정상 형식의 traceId") {
        val uuid = UUID.randomUUID().toString()

        Then("UUID 는 그대로 통과한다") {
            TraceIdSanitizer.isValid(uuid) shouldBe true
            TraceIdSanitizer.sanitize(uuid) shouldBe uuid
        }

        Then("영숫자·점·하이픈·언더스코어 조합도 통과한다 (W3C trace-id 등)") {
            TraceIdSanitizer.isValid("4bf92f3577b34da6a3ce929d0e0e4736") shouldBe true
            TraceIdSanitizer.isValid("svc-a.1_2-3") shouldBe true
        }
    }

    Given("오염된 traceId") {
        val cases =
            mapOf(
                "개행 주입(로그 위조)" to "abc\nFAKE LOG LINE",
                "캐리지리턴 주입" to "abc\r\nSet-Cookie: x=y",
                "공백 포함" to "abc def",
                "허용 밖 문자" to "abc<script>",
                "과대 길이(65자)" to "a".repeat(65),
                "빈 문자열" to "",
            )

        cases.forEach { (label, raw) ->
            Then("거부되고 새 UUID 로 대체된다 — $label") {
                TraceIdSanitizer.isValid(raw) shouldBe false
                val sanitized = TraceIdSanitizer.sanitize(raw)
                sanitized shouldNotBe raw
                TraceIdSanitizer.isValid(sanitized) shouldBe true
            }
        }
    }

    Given("헤더가 아예 없을 때") {
        Then("새 UUID 를 만든다") {
            val sanitized = TraceIdSanitizer.sanitize(null)
            TraceIdSanitizer.isValid(sanitized) shouldBe true
        }
    }
})