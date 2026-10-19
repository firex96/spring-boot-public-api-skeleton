package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * e2e 가 만든 auth 행을 SQL 로 지운다 — 스켈레톤 전용 스키마(`skeleton_auth`)에 실행마다 계정·클라이언트가 쌓이지 않게.
 *
 * 서비스 컴포지트(`isSystem`·비활성 가드가 있고 검사 대상 코드)는 쓰지 않는다. 논리 FK 만이라 자식 → 부모 순서로 지우고,
 * 끝에 같은 id 집합으로 14 테이블을 재조회해 남은 행이 있으면 실패시킨다 — 정리 누락이 조용히 지나가지 않게.
 * `audit_logs` 는 남긴다(사실 기록). Redis 의 세션·로그인 카운터도 함께 비운다.
 */
class E2eResourceJanitor(
    authJdbcTemplate: JdbcTemplate,
    authTransactionManager: PlatformTransactionManager,
    private val sessionCacheBizService: SessionCacheBizService,
    private val loginAttemptBizService: LoginAttemptBizService,
) {
    private val jdbc = NamedParameterJdbcTemplate(authJdbcTemplate)
    private val tx = TransactionTemplate(authTransactionManager)

    /** 종류별 삭제 행 수(`children` 은 자식 테이블 합). */
    fun cleanup(r: E2eResources): Map<String, Int> {
        val counts = linkedMapOf("users" to 0, "groups" to 0, "permissions" to 0, "oauth2Clients" to 0, "blockedIps" to 0, "children" to 0)
        if (r.userLoginIds.isEmpty() && r.groupIds.isEmpty() && r.permissionIds.isEmpty() && r.oauth2ClientIds.isEmpty() && r.blockedIps.isEmpty()) {
            return counts
        }

        val userIds: List<Long> = if (r.userLoginIds.isEmpty()) {
            emptyList()
        } else {
            jdbc.queryForList("SELECT id FROM users WHERE login_id IN (:ids)", MapSqlParameterSource("ids", r.userLoginIds.toList()), Long::class.java).filterNotNull()
        }
        userIds.forEach { sessionCacheBizService.deleteAllUserSessions(it) }
        r.userLoginIds.forEach { loginAttemptBizService.unlockUserAccount(it) }

        tx.executeWithoutResult {
            if (userIds.isNotEmpty()) {
                USER_CHILD_TABLES.forEach { counts["children"] = counts.getValue("children") + deleteIn(it, "user_id", userIds) }
                counts["users"] = deleteIn("users", "id", userIds)
            }
            if (r.groupIds.isNotEmpty()) {
                counts["children"] = counts.getValue("children") + deleteIn("role_permissions", "role_id", r.groupIds) + deleteIn("user_roles", "role_id", r.groupIds)
                counts["groups"] = deleteIn("roles", "id", r.groupIds)
            }
            if (r.permissionIds.isNotEmpty()) {
                counts["children"] = counts.getValue("children") + deleteIn("role_permissions", "permission_id", r.permissionIds)
                counts["permissions"] = deleteIn("permissions", "id", r.permissionIds)
            }
            if (r.oauth2ClientIds.isNotEmpty()) {
                CLIENT_CHILD_TABLES.forEach { counts["children"] = counts.getValue("children") + deleteIn(it, "client_id", r.oauth2ClientIds) }
                counts["oauth2Clients"] = deleteIn("oauth2_clients", "client_id", r.oauth2ClientIds)
            }
            if (r.blockedIps.isNotEmpty()) {
                counts["blockedIps"] = deleteIn("blocked_ips", "ip_address", r.blockedIps)
            }
        }

        verifyNothingLeft(r, userIds)
        return counts
    }

    private fun deleteIn(table: String, column: String, ids: Collection<Any>): Int =
        jdbc.update("DELETE FROM $table WHERE $column IN (:ids)", MapSqlParameterSource("ids", ids.toList()))

    private fun countIn(table: String, column: String, ids: Collection<Any>): Int =
        if (ids.isEmpty()) 0 else jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $column IN (:ids)", MapSqlParameterSource("ids", ids.toList()), Int::class.java) ?: 0

    private fun verifyNothingLeft(r: E2eResources, userIds: List<Long>) {
        val left = linkedMapOf<String, Int>()
        (USER_CHILD_TABLES + "users").forEach { t -> countIn(t, if (t == "users") "id" else "user_id", userIds).takeIf { it > 0 }?.let { left[t] = it } }
        countIn("role_permissions", "role_id", r.groupIds).takeIf { it > 0 }?.let { left["role_permissions(role)"] = it }
        countIn("user_roles", "role_id", r.groupIds).takeIf { it > 0 }?.let { left["user_roles(role)"] = it }
        countIn("roles", "id", r.groupIds).takeIf { it > 0 }?.let { left["roles"] = it }
        countIn("role_permissions", "permission_id", r.permissionIds).takeIf { it > 0 }?.let { left["role_permissions(permission)"] = it }
        countIn("permissions", "id", r.permissionIds).takeIf { it > 0 }?.let { left["permissions"] = it }
        (CLIENT_CHILD_TABLES + "oauth2_clients").forEach { t -> countIn(t, "client_id", r.oauth2ClientIds).takeIf { it > 0 }?.let { left[t] = it } }
        countIn("blocked_ips", "ip_address", r.blockedIps).takeIf { it > 0 }?.let { left["blocked_ips"] = it }
        check(left.isEmpty()) { "[e2e cleanup] 잔재: $left" }
    }

    companion object {
        private val USER_CHILD_TABLES = listOf(
            "user_sessions",
            "user_roles",
            "user_social_accounts",
            "password_reset_tokens",
            "email_verification_tokens",
            "oauth2_authorization_codes",
            "oauth2_access_tokens",
            "oauth2_refresh_tokens",
            "user_mfa_credentials",
            "user_mfa_recovery_codes",
        )
        private val CLIENT_CHILD_TABLES = listOf("oauth2_authorization_codes", "oauth2_access_tokens", "oauth2_refresh_tokens")
    }
}