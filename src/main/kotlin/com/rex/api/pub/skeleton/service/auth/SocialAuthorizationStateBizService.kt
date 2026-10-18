package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.SocialAuthorizationStateDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.util.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * 소셜 로그인 state — 발급한 브라우저 세션에 묶고, 콜백에서 **한 번만** 소비한다.
 *
 * 세션 속성이 아니라 Redis 키에 두는 이유: 세션 저장소는 요청마다 읽은 사본을 쓰므로, 같은 세션의 두 요청이
 * 삭제 저장 전에 같은 값을 읽으면 둘 다 통과한다. `GETDEL` 은 원자적이라 동시 요청 중 정확히 하나만 값을 얻는다.
 */
@Service
class SocialAuthorizationStateBizService(
    private val redisTemplate: StringRedisTemplate,
    private val jsonMapper: JsonMapper,
) {
    private val log = logger()
    private val secureRandom = SecureRandom()

    fun issue(browserSessionId: String, provider: SocialProvider): String {
        val bytes = ByteArray(STATE_BYTES).also { secureRandom.nextBytes(it) }
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        redisTemplate.opsForValue().set(
            KEY_PREFIX + state,
            jsonMapper.writeValueAsString(SocialAuthorizationStateDto(browserSessionId = browserSessionId, provider = provider)),
            TTL,
        )
        return state
    }

    /** 키를 먼저 지우며 꺼낸다 — 일치하지 않아도 그 state 는 다시 쓸 수 없다. */
    fun consume(browserSessionId: String, provider: SocialProvider, state: String?): Boolean {
        if (state.isNullOrBlank()) return false
        val stored = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state) ?: return false
        val bound =
            try {
                jsonMapper.readValue(stored, SocialAuthorizationStateDto::class.java)
            } catch (e: Exception) {
                log.warn("소셜 state 값을 읽지 못했다: {}", e.message)
                return false
            }
        val sameSession = MessageDigest.isEqual(bound.browserSessionId.toByteArray(), browserSessionId.toByteArray())
        return sameSession && bound.provider == provider
    }

    companion object {
        const val KEY_PREFIX = "browser:social-state:"
        val TTL: Duration = Duration.ofSeconds(300)
        private const val STATE_BYTES = 32
    }
}