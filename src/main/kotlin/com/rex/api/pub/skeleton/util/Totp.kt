package com.rex.api.pub.skeleton.util

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP (RFC 6238) — HMAC-SHA1, 6자리, 30초 구간. 인증 앱(Google Authenticator 등)의 기본값과 같다.
 *
 * 시계 오차를 위해 현재 구간과 앞뒤 한 구간까지 받는다. 같은 구간의 재사용 방지는 호출자가 일치한 구간 번호로 한다
 * (`UserMfaEntityService.markTotpStepUsed` — DB 조건부 UPDATE).
 */
object Totp {
    const val DIGITS = 6
    const val PERIOD_SECONDS = 30L
    private const val ALLOWED_DRIFT_STEPS = 1
    private const val ALGORITHM = "HmacSHA1"
    private const val MODULUS = 1_000_000 // 10^DIGITS

    fun step(instant: Instant): Long = Math.floorDiv(instant.epochSecond, PERIOD_SECONDS)

    /** RFC 4226 §5.3 동적 절단 — 구간 [step] 의 코드(앞자리 0 포함 [DIGITS]자리). */
    fun code(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(secret, ALGORITHM)) }
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return (binary % MODULUS).toString().padStart(DIGITS, '0')
    }

    /**
     * [code] 가 [now] 의 구간 또는 앞뒤 한 구간의 코드와 같으면 그 구간 번호, 아니면 null. 형식이 틀린 입력은 바로 null.
     * 비교는 상수 시간이다.
     */
    fun matchStep(secret: ByteArray, code: String, now: Instant): Long? {
        val normalized = code.filterNot { it.isWhitespace() }
        if (normalized.length != DIGITS || !normalized.all { it.isDigit() }) return null
        val current = step(now)
        return (-ALLOWED_DRIFT_STEPS..ALLOWED_DRIFT_STEPS)
            .map { current + it }
            .firstOrNull { MessageDigest.isEqual(code(secret, it).toByteArray(), normalized.toByteArray()) }
    }
}