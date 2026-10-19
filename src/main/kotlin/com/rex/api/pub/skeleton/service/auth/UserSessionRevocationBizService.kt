package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 세션 일괄 폐기의 유일한 경로 — 호출자는 폐기 사유와 남길 세션(없으면 null)만 정한다.
 *
 * 대상은 폐기 **전에** 모으고 **그 ID 만** 폐기한다 — 폐기 뒤에 "폐기되지 않은 세션" 으로 캐시를 찾으면 방금 폐기한 세션이 빠져,
 * 캐시가 남은 동안(액세스 토큰 만료까지) 폐기된 세션의 토큰이 계속 통과한다. 사용자 단위 bulk UPDATE 를 쓰지 않는 이유도 같다 —
 * 수집과 UPDATE 사이에 생긴 세션까지 폐기해 캐시 삭제 대상과 어긋난다. 캐시 제거 규칙은 `SessionCacheBizService.evictRevokedSessions`.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserSessionRevocationBizService(
    private val userSessionEntityService: UserSessionEntityService,
    private val sessionCacheBizService: SessionCacheBizService,
) {
    /** [exceptSessionId] 를 뺀 그 사용자의 살아 있는 세션을 [reason] 으로 폐기하고 캐시를 지운다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun revokeAll(userId: Long, reason: String, exceptSessionId: String?) {
        val sessionIds = userSessionEntityService.getUnrevokedByUserId(userId).map { it.sessionId }.filter { it != exceptSessionId }
        userSessionEntityService.revokeAll(sessionIds, reason)
        sessionCacheBizService.evictRevokedSessions(sessionIds, userId)
    }
}