package com.rex.api.pub.skeleton.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * TOTP — RFC 6238 부록 B 의 SHA1 벡터(시크릿 "12345678901234567890", 8자리 기대값의 끝 6자리)와 ±1 구간 허용 경계.
 */
class TotpTest : StringSpec({
    val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    "RFC 6238 부록 B SHA1 벡터" {
        listOf(
            59L to "287082",
            1_111_111_109L to "081804",
            1_111_111_111L to "050471",
            1_234_567_890L to "005924",
            2_000_000_000L to "279037",
            20_000_000_000L to "353130",
        ).forEach { (epochSecond, expected) ->
            Totp.code(secret, Totp.step(Instant.ofEpochSecond(epochSecond))) shouldBe expected
        }
    }

    "현재·앞·뒤 한 구간의 코드는 그 구간 번호로 통과하고, 두 구간 떨어지면 거부한다" {
        val now = Instant.ofEpochSecond(1_234_567_890L)
        val current = Totp.step(now)
        listOf(-1L, 0L, 1L).forEach { offset ->
            Totp.matchStep(secret, Totp.code(secret, current + offset), now) shouldBe current + offset
        }
        Totp.matchStep(secret, Totp.code(secret, current - 2), now).shouldBeNull()
        Totp.matchStep(secret, Totp.code(secret, current + 2), now).shouldBeNull()
    }

    "형식이 틀린 입력은 비교하지 않고 거부한다 — 공백은 허용" {
        val now = Instant.ofEpochSecond(59L)
        Totp.matchStep(secret, "287 082", now) shouldBe Totp.step(now)
        Totp.matchStep(secret, "28708", now).shouldBeNull()
        Totp.matchStep(secret, "2870820", now).shouldBeNull()
        Totp.matchStep(secret, "28708a", now).shouldBeNull()
    }
})