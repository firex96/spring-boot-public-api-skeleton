package com.rex.api.pub.skeleton.util

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 토큰 생성 및 해싱 유틸리티.
 *
 * 비밀번호 재설정, 이메일 인증 등의 토큰을 안전하게 처리합니다.
 * - 토큰은 DB에 해시로만 저장
 * - 원본 토큰은 사용자에게 전송 (이메일 등)
 */
@Component
class TokenHasher {
    companion object {
        private const val TOKEN_LENGTH = 32
        private const val HASH_ALGORITHM = "SHA-256"
    }

    private val secureRandom = SecureRandom()

    /**
     * 새로운 토큰을 생성합니다.
     *
     * @return Pair(원본 토큰, 해시된 토큰)
     */
    fun generateToken(): TokenPair {
        val bytes = ByteArray(TOKEN_LENGTH)
        secureRandom.nextBytes(bytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hashedToken = hash(rawToken)
        return TokenPair(rawToken, hashedToken)
    }

    /**
     * 토큰을 SHA-256으로 해싱합니다.
     *
     * @param rawToken 원본 토큰
     * @return 해시된 토큰 (Base64 인코딩)
     */
    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hashBytes = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)
    }

    /**
     * 토큰을 검증합니다.
     *
     * @param rawToken 사용자가 제공한 원본 토큰
     * @param storedHash DB에 저장된 해시
     * @return 일치 여부
     */
    fun verify(rawToken: String, storedHash: String): Boolean {
        val computedHash = hash(rawToken)
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * 토큰 쌍 (원본, 해시)
     */
    data class TokenPair(
        val rawToken: String, // 사용자에게 전송할 원본 토큰
        val hashedToken: String, // DB에 저장할 해시된 토큰
    )
}