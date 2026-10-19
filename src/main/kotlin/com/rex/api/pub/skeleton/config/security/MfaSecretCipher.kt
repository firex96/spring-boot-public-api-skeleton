package com.rex.api.pub.skeleton.config.security

import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 시크릿 암호화 — AES-256-GCM(12바이트 랜덤 IV, 128비트 태그). 저장 형식은 `base64url(iv ‖ ciphertext‖tag)`.
 *
 * AAD 는 `"mfa:" + userId` 다 — 다른 사용자 행으로 옮긴 암호문은 복호화되지 않는다(태그 불일치).
 * 키는 `mfa.secret-encryption-key`(base64 32바이트)이고 길이가 다르면 기동이 실패한다.
 */
@Component
class MfaSecretCipher(
    properties: MfaProperties,
) {
    private val key =
        Base64.getDecoder().decode(properties.secretEncryptionKey.trim()).let { raw ->
            require(raw.size == KEY_BYTES) { "mfa.secret-encryption-key 는 base64 로 인코딩한 32바이트여야 한다(현재 ${raw.size}바이트)" }
            SecretKeySpec(raw, "AES")
        }
    private val random = SecureRandom()

    fun encrypt(userId: Long, plain: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad(userId))
        }
        val sealed = cipher.doFinal(plain)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(iv.size + sealed.size).put(iv).put(sealed).array())
    }

    /** 변조·다른 사용자 행·다른 키면 [IllegalStateException]. */
    fun decrypt(userId: Long, stored: String): ByteArray {
        val bytes = Base64.getUrlDecoder().decode(stored)
        check(bytes.size > IV_BYTES) { "MFA 시크릿 형식이 아니다" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            updateAAD(aad(userId))
        }
        return try {
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw IllegalStateException("MFA 시크릿을 복호화할 수 없다 — 키가 바뀌었거나 다른 사용자의 값이다", e)
        }
    }

    private fun aad(userId: Long): ByteArray = "mfa:$userId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}