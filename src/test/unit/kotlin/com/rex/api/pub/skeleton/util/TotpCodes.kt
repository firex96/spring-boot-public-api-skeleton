package com.rex.api.pub.skeleton.util

import java.time.Instant

/**
 * 테스트용 인증 앱 — 시크릿(Base32)으로 다음에 쓸 수 있는 TOTP 코드를 만든다. 통합·e2e 테스트도 쓴다(클래스패스가 유닛 출력을 포함한다).
 *
 * 서버는 같은 구간을 한 번만 받으므로, 마지막으로 낸 구간보다 **뒤의** 구간을 고른다. 현재 구간과 다음 구간(서버의 ±1 허용)까지 쓰고,
 * 둘 다 썼으면 다음 구간이 될 때까지 기다린다. 앞 구간(-1)은 서버 시계가 그사이 넘어가면 거부되므로 쓰지 않는다.
 */
class TotpCodes(
    base32Secret: String,
) {
    private val secret = Base32.decode(base32Secret)
    private var lastStep = Long.MIN_VALUE

    @Synchronized
    fun next(): String {
        while (true) {
            val now = Totp.step(Instant.now())
            val candidate = maxOf(now, lastStep + 1)
            if (candidate <= now + 1) {
                lastStep = candidate
                return Totp.code(secret, candidate)
            }
            Thread.sleep(millisUntilNextStep())
        }
    }

    /** 지금 창(±1)의 어떤 코드와도 다른 6자리 — 틀린 코드 시험용. */
    fun wrong(): String {
        val now = Totp.step(Instant.now())
        val valid = (now - 1..now + 1).map { Totp.code(secret, it) }.toSet()
        return generateSequence(0) { it + 1 }.map { it.toString().padStart(Totp.DIGITS, '0') }.first { it !in valid }
    }

    private fun millisUntilNextStep(): Long = Totp.PERIOD_SECONDS * 1000 - Instant.now().toEpochMilli() % (Totp.PERIOD_SECONDS * 1000) + 50
}