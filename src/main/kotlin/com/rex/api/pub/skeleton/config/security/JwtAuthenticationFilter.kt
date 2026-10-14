package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.auth.JwtTokenBizService
import com.rex.api.pub.skeleton.util.logger
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenBizService: JwtTokenBizService,
    private val authBizService: AuthBizService,
) : OncePerRequestFilter() {
    private val log = logger()

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val token = extractToken(request)

            if (token != null) {
                val payload = jwtTokenBizService.validateToken(token)

                if (payload != null && !payload.isRefreshToken) {
                    // 세션 유효성 확인 (Redis 캐시 → DB)
                    val sessionInfo = authBizService.validateSession(payload.sessionId)

                    if (sessionInfo != null && !sessionInfo.revoked) {
                        val authorities =
                            buildList {
                                // 그룹을 ROLE로 변환
                                sessionInfo.groups.forEach { group ->
                                    add(SimpleGrantedAuthority("ROLE_$group"))
                                }
                                // 권한 추가
                                sessionInfo.permissions.forEach { permission ->
                                    add(SimpleGrantedAuthority(permission))
                                }
                            }

                        val authentication =
                            UsernamePasswordAuthenticationToken(
                                AuthenticatedUser(
                                    userId = sessionInfo.userId,
                                    sessionId = sessionInfo.sessionId,
                                    groups = sessionInfo.groups,
                                    permissions = sessionInfo.permissions,
                                ),
                                null,
                                authorities,
                            )

                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("JWT authentication failed: {}", e.message)
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader(AUTHORIZATION_HEADER)
        return if (header != null && header.startsWith(BEARER_PREFIX)) {
            header.substring(BEARER_PREFIX.length)
        } else {
            null
        }
    }
}