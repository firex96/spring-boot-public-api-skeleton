package com.rex.api.pub.skeleton.util

/**
 * RFC 4648 Base32 (대문자 알파벳, 패딩 없음) — 인증 앱이 읽는 TOTP 시크릿 형식이다.
 *
 * 디코드는 공백·하이픈·소문자·패딩(`=`)을 허용한다(사용자가 4자씩 끊어 옮겨 적은 값).
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val cleaned = text.uppercase().filterNot { it == ' ' || it == '-' || it == '=' }
        val out = java.io.ByteArrayOutputStream(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in cleaned) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Base32 가 아닌 문자: $c" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}