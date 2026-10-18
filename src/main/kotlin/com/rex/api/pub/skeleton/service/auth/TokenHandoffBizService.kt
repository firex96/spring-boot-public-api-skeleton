package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.util.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * 소셜 콜백이 발급한 토큰을 URL 대신 서버에 잠시 두었다가 **한 번만** 넘긴다.
 *
 * 리다이렉트 쿼리에 실린 토큰은 브라우저 이력·Referer·접근 로그에 남는다. 그래서 콜백은 토큰을 여기 두고
 * 쿼리 없이 리다이렉트하며, 페이지가 CSRF 헤더를 실은 `POST /auth/session/token` 으로 꺼낸다.
 * 키는 사용자 세션 ID(access 토큰 jti)이고, 꺼낼 때 `GETDEL` 이라 동시 요청 중 하나만 받는다.
 */
@Service
class TokenHandoffBizService(
    private val redisTemplate: StringRedisTemplate,
    private val jsonMapper: JsonMapper,
) {
    private val log = logger()

    fun put(userSessionId: String, tokens: TokenResponseDto) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userSessionId, jsonMapper.writeValueAsString(tokens), TTL)
    }

    fun take(userSessionId: String): TokenResponseDto? {
        val stored = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + userSessionId) ?: return null
        return try {
            jsonMapper.readValue(stored, TokenResponseDto::class.java)
        } catch (e: Exception) {
            log.warn("교환 대기 토큰을 읽지 못했다: {}", e.message)
            null
        }
    }

    companion object {
        const val KEY_PREFIX = "browser:token-handoff:"
        val TTL: Duration = Duration.ofSeconds(60)
    }
}