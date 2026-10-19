package com.rex.api.pub.skeleton.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.security.SecureRandom

/** Base32 — RFC 4648 §10 벡터(패딩 없이)와 왕복, 사람이 옮겨 적은 형식(공백·소문자·하이픈) 디코드. */
class Base32Test : StringSpec({
    "RFC 4648 §10 벡터" {
        listOf(
            "" to "",
            "f" to "MY",
            "fo" to "MZXQ",
            "foo" to "MZXW6",
            "foob" to "MZXW6YQ",
            "fooba" to "MZXW6YTB",
            "foobar" to "MZXW6YTBOI",
        ).forEach { (plain, encoded) ->
            Base32.encode(plain.toByteArray()) shouldBe encoded
            String(Base32.decode(encoded)) shouldBe plain
        }
    }

    "임의 바이트 왕복 — 20바이트 TOTP 시크릿" {
        repeat(50) {
            val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
            Base32.decode(Base32.encode(bytes)).toList() shouldBe bytes.toList()
        }
    }

    "공백·소문자·하이픈·패딩을 허용하고, 알파벳 밖 문자는 거부한다" {
        String(Base32.decode("mzxw 6ytb-oi======")) shouldBe "foobar"
        shouldThrow<IllegalArgumentException> { Base32.decode("MZXW1") }
    }
})