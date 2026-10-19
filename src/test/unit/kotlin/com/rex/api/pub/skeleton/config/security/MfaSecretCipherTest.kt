package com.rex.api.pub.skeleton.config.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

/** TOTP 시크릿 암호화 — 왕복, 변조·다른 사용자 행·다른 키 거부, 키 길이 검증. */
class MfaSecretCipherTest : StringSpec({
    fun cipher(key: ByteArray = ByteArray(32) { it.toByte() }) =
        MfaSecretCipher(MfaProperties(secretEncryptionKey = Base64.getEncoder().encodeToString(key)))

    val secret = "12345678901234567890".toByteArray()

    "같은 사용자로 복호화하면 원래 시크릿 — 같은 값도 암호문은 매번 다르다(랜덤 IV)" {
        val c = cipher()
        val first = c.encrypt(7L, secret)
        c.decrypt(7L, first).toList() shouldBe secret.toList()
        c.encrypt(7L, secret) shouldNotBe first
    }

    "다른 사용자 행으로 옮긴 암호문은 복호화되지 않는다(AAD)" {
        val stored = cipher().encrypt(7L, secret)
        shouldThrow<IllegalStateException> { cipher().decrypt(8L, stored) }
    }

    "한 바이트라도 바뀌면 거부한다(GCM 태그)" {
        val stored = Base64.getUrlDecoder().decode(cipher().encrypt(7L, secret))
        stored[stored.size - 1] = (stored[stored.size - 1].toInt() xor 1).toByte()
        shouldThrow<IllegalStateException> { cipher().decrypt(7L, Base64.getUrlEncoder().withoutPadding().encodeToString(stored)) }
    }

    "다른 키로는 복호화되지 않는다" {
        val stored = cipher().encrypt(7L, secret)
        shouldThrow<IllegalStateException> { cipher(ByteArray(32) { 9 }).decrypt(7L, stored) }
    }

    "키가 32바이트가 아니면 기동하지 않는다" {
        shouldThrow<IllegalArgumentException> { cipher(ByteArray(16)) }
    }
})