package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.PermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
        private const val USER_PERMISSIONS_KEY_PREFIX = "user:permissions:"

        // 세션 캐시 키와 폐기 표식 키는 해시 태그 {sessionId} 를 공유한다 — Redis 클러스터에서 같은 슬롯이라 한 스크립트로 원자적으로 다룬다
        fun sessionKey(sessionId: String): String = "session:{$sessionId}"

        fun revokedKey(sessionId: String): String = "session:revoked:{$sessionId}"

        /** 폐기 표식이 없을 때만 캐시에 쓴다(원자적). 1 = 썼다, 0 = 폐기됨. KEYS[1]=세션 키, KEYS[2]=표식 키, ARGV = 값·TTL(ms). */
        private val CACHE_UNLESS_REVOKED: RedisScript<Long> =
            RedisScript.of(
                "if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) return 1",
                Long::class.java,
            )

        /** 폐기 표식 수명 — 폐기 직전에 DB 를 읽은 요청이 캐시에 쓰기까지의 경쟁 구간만 덮으면 된다. */
        private val REVOKED_MARKER_TTL = Duration.ofMinutes(5)
        private val PERMISSIONS_CACHE_TTL = Duration.ofMinutes(5)
    }

    fun getSession(sessionId: String): SessionInfo? {
        val key = sessionKey(sessionId)

        // 1. Redis 캐시 조회
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            val session =
                try {
                    objectMapper.readValue(cached, SessionInfo::class.java)
                } catch (e: Exception) {
                    // 읽지 못하는 캐시(배포 전 형식 등)는 버리고 DB 에서 다시 읽는다 — null 을 돌려주면 살아 있는 세션이 인증 실패가 된다
                    log.warn("Failed to parse cached session, reloading from DB: {}", sessionId, e)
                    redisTemplate.delete(key)
                    null
                }
            if (session != null) {
                if (session.revoked || session.expiresAt.isBefore(Instant.now())) {
                    deleteSession(sessionId)
                    return null
                }
                return session
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

        // 4. SessionInfo 생성 — 폐기 표식이 없을 때만 캐시에 쓴다(원자적).
        //    DB 를 읽은 뒤 폐기가 커밋됐다면 표식이 이미 있으므로 폐기 전 값을 캐시에 올리지도, 돌려주지도 않는다
        val sessionInfo = createSessionInfo(dbSession, groups, permissions)
        return sessionInfo.takeIf { cacheSessionUnlessRevoked(it) }
    }

    /**
     * 폐기 표식이 없을 때만 캐시에 쓴다 — 표식 확인과 쓰기가 한 스크립트라 그 사이에 폐기가 끼어들 수 없다.
     * 폐기 쪽([evictRevokedSessions])은 표식을 먼저 쓰고 캐시를 지우므로, 폐기가 끝난 뒤에는 폐기 전 값이 캐시에 다시 나타나지 않는다.
     * 반환값: 폐기되지 않았으면 true(만료가 지나 쓰지 않은 경우도 true), 폐기 표식이 있으면 false.
     */
    fun cacheSessionUnlessRevoked(sessionInfo: SessionInfo): Boolean {
        val ttl = Duration.between(Instant.now(), sessionInfo.expiresAt)
        if (ttl.isNegative || ttl.isZero) return true
        val written =
            redisTemplate.execute(
                CACHE_UNLESS_REVOKED,
                listOf(sessionKey(sessionInfo.sessionId), revokedKey(sessionInfo.sessionId)),
                objectMapper.writeValueAsString(sessionInfo),
                ttl.toMillis().toString(),
            )
        return written == 1L
    }

    fun cacheSession(sessionInfo: SessionInfo) {
        val key = sessionKey(sessionInfo.sessionId)
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
        val key = sessionKey(sessionId)
        redisTemplate.delete(key)
    }

    /** 폐기한 세션 하나의 캐시를 지운다 — [evictRevokedSessions] 와 같은 규칙(커밋 뒤, 표식 먼저). */
    fun invalidateSession(sessionId: String) {
        evictRevokedSessions(listOf(sessionId), userId = null)
    }

    /**
     * 살아 있는 세션들의 캐시를 지워 다음 요청에 다시 읽게 한다 — 권한 변경 반영용.
     * **폐기 뒤에 부르지 않는다**: 폐기된 세션은 조회에 잡히지 않아 캐시가 남는다. 폐기에는 [evictRevokedSessions].
     */
    fun deleteAllUserSessions(userId: Long) {
        val keys = userSessionEntityService.getUnrevokedByUserId(userId).map { sessionKey(it.sessionId) }
        redisTemplate.delete(keys + (USER_PERMISSIONS_KEY_PREFIX + userId))
    }

    /**
     * 폐기한 세션들의 캐시를 지운다([userId] 가 있으면 권한 캐시도). 대상은 호출자가 폐기한 바로 그 세션 ID 다.
     *
     * - 트랜잭션 안이면 **커밋 뒤에** 한다 — 커밋 전에 지우면 다른 요청이 캐시 miss 로 아직 폐기 전인 행을 읽어 다시 올린다.
     * - **폐기 표식을 먼저 쓰고** 캐시를 지운다 — 폐기 전에 DB 를 읽은 요청의 늦은 캐시 쓰기는 [cacheSessionUnlessRevoked] 가 표식을 보고 막는다.
     */
    fun evictRevokedSessions(sessionIds: Collection<String>, userId: Long?) {
        if (sessionIds.isEmpty() && userId == null) return
        val evict = {
            sessionIds.forEach { redisTemplate.opsForValue().set(revokedKey(it), "1", REVOKED_MARKER_TTL) }
            redisTemplate.delete(sessionIds.map { sessionKey(it) } + listOfNotNull(userId?.let { USER_PERMISSIONS_KEY_PREFIX + it }))
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        evict()
                    }
                },
            )
        } else {
            evict()
        }
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
            authenticatedAt = session.createdAt.atZone(ZoneId.systemDefault()).toInstant(),
            revoked = session.isRevoked,
        )
}