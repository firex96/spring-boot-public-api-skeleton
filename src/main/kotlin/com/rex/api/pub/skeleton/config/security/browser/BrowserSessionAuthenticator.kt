package com.rex.api.pub.skeleton.config.security.browser

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.model.auth.BrowserPrincipalModel
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component

/**
 * 브라우저 세션에 사용자를 로그인시킨다 — 세션 ID 를 바꾼 뒤(세션 고정 방지) SecurityContext 를 세션에 저장한다.
 *
 * principal 의 목록은 `ArrayList` 로 복사한다 — 세션은 Redis 에 JDK 직렬화로 저장되므로 직렬화 가능한 구현이어야 한다.
 */
@Component
class BrowserSessionAuthenticator {
    private val securityContextRepository = HttpSessionSecurityContextRepository()

    fun signIn(request: HttpServletRequest, response: HttpServletResponse, principal: BrowserPrincipalModel) {
        request.getSession(true)
        request.changeSessionId()

        val user =
            AuthenticatedUser(
                userId = principal.userId,
                sessionId = principal.sessionId,
                groups = ArrayList(principal.groups),
                permissions = ArrayList(principal.permissions),
            )
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities()) }
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }
}