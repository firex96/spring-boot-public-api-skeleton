package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.PermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class SessionCacheBizService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: JsonMapper,
    private val userSessionEntityService: UserSessionEntityService,
    private val userGroupEntityService: UserGroupEntityService,
    private val permissionEntityService: PermissionEntityService,
) {
    private val log = logger()

    companion object {
        private const val SESSION_KEY_PREFIX = "session:"
        private const val USER_PERMISSIONS_KEY_PREFIX = "user:permissions:"
        private val PERMISSIONS_CACHE_TTL = Duration.ofMinutes(5)
    }

    fun getSession(sessionId: String): SessionInfo? {
        val key = SESSION_KEY_PREFIX + sessionId

        // 1. Redis 캐시 조회
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            return try {
                val session = objectMapper.readValue(cached, SessionInfo::class.java)
                if (session.revoked || session.expiresAt.isBefore(Instant.now())) {
                    deleteSession(sessionId)
                    null
                } else {
                    session
                }
            } catch (e: Exception) {
                log.warn("Failed to parse cached session: {}", sessionId, e)
                null
            }
        }

        // 2. Cache Miss → DB 조회
        val dbSession = userSessionEntityService.getUnrevokedBySessionId(sessionId) ?: return null

        if (dbSession.accessTokenExpiresAt.isBefore(LocalDateTime.now())) {
            return null
        }

        // 3. 권한 정보 조회
        val groups = getGroupCodes(dbSession.userId)
        val permissions = getPermissionCodes(dbSession.userId)

        // 4. SessionInfo 생성 및 캐시 저장
        val sessionInfo = createSessionInfo(dbSession, groups, permissions)
        cacheSession(sessionInfo)

        return sessionInfo
    }

    fun cacheSession(sessionInfo: SessionInfo) {
        val key = SESSION_KEY_PREFIX + sessionInfo.sessionId
        val ttl = Duration.between(Instant.now(), sessionInfo.expiresAt)

        if (ttl.isNegative || ttl.isZero) {
            return
        }

        try {
            val json = objectMapper.writeValueAsString(sessionInfo)
            redisTemplate.opsForValue().set(key, json, ttl)
        } catch (e: Exception) {
            log.error("Failed to cache session: {}", sessionInfo.sessionId, e)
        }
    }

    fun deleteSession(sessionId: String) {
        val key = SESSION_KEY_PREFIX + sessionId
        redisTemplate.delete(key)
    }

    fun invalidateSession(sessionId: String) {
        deleteSession(sessionId)
    }

    fun deleteAllUserSessions(userId: Long) {
        val sessions = userSessionEntityService.getUnrevokedByUserId(userId)
        sessions.forEach { session ->
            deleteSession(session.sessionId)
        }
        // 권한 캐시도 삭제
        deleteUserPermissionsCache(userId)
    }

    fun deleteUserPermissionsCache(userId: Long) {
        val key = USER_PERMISSIONS_KEY_PREFIX + userId
        redisTemplate.delete(key)
    }

    fun invalidateGroupUsersPermissionsCache(groupId: Long) {
        userGroupEntityService.getUserIdsByGroupId(groupId).forEach { deleteUserPermissionsCache(it) }
    }

    fun getSessionByUserId(userId: Long): SessionInfo? {
        // 사용자의 활성 세션 중 하나를 반환 (캐시된 정보 조회용)
        val sessions = userSessionEntityService.getUnrevokedByUserId(userId)
        if (sessions.isEmpty()) {
            return null
        }

        // 가장 최근 세션 사용
        val latestSession = sessions.maxByOrNull { it.lastActivityAt } ?: return null
        return getSession(latestSession.sessionId)
    }

    private fun getGroupCodes(userId: Long): List<String> = userGroupEntityService.getActiveGroupCodesByUserId(userId)

    private fun getPermissionCodes(userId: Long): List<String> = permissionEntityService.getAllByUserId(userId).map { it.code }

    private fun createSessionInfo(
        session: UserSessionRecordDto,
        groups: List<String>,
        permissions: List<String>,
    ): SessionInfo =
        SessionInfo(
            userId = session.userId,
            sessionId = session.sessionId,
            groups = groups,
            permissions = permissions,
            deviceType = session.deviceType,
            ipAddress = session.ipAddress,
            issuedAt = session.accessTokenIssuedAt.atZone(ZoneId.systemDefault()).toInstant(),
            expiresAt = session.accessTokenExpiresAt.atZone(ZoneId.systemDefault()).toInstant(),
            revoked = session.isRevoked,
        )
}