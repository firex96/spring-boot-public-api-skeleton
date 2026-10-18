package com.rex.api.pub.skeleton.config.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.io.Serializable

/**
 * 인증된 사용자 principal. API 체인은 요청마다 JWT 로 만들고, 브라우저 체인은 세션의 SecurityContext 에 담는다.
 *
 * 브라우저 세션은 Spring Session Redis(JDK 직렬화)에 저장되므로 [Serializable] 이다 — 세션에 넣는 쪽은
 * `groups`·`permissions` 를 `ArrayList` 로 복사해 직렬화 가능한 구현을 보장한다.
 */
data class AuthenticatedUser(
    val userId: Long,
    val sessionId: String,
    val groups: List<String>,
    val permissions: List<String>,
) : Serializable {
    /** Spring Security 권한 — 그룹은 `ROLE_{group}`, 권한 코드는 그대로. API 체인(JWT)과 브라우저 체인이 같은 규칙을 쓴다. */
    fun authorities(): List<GrantedAuthority> = groups.map { SimpleGrantedAuthority("ROLE_$it") } + permissions.map(::SimpleGrantedAuthority)

    companion object {
        private const val serialVersionUID = 1L
    }
}