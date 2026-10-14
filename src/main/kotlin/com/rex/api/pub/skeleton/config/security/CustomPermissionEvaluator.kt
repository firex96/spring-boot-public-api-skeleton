package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.service.auth.AuthorizationBizService
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.io.Serializable

/**
 * 커스텀 권한 평가자
 *
 * @PreAuthorize 어노테이션에서 권한 코드를 사용할 수 있게 합니다.
 *
 * 사용 예시:
 * - @PreAuthorize("hasPermission(null, 'user:read')") - 권한 코드로 확인
 * - @PreAuthorize("hasPermission(#userId, 'user', 'read')") - 리소스 타입과 액션으로 확인
 */
@Component
class CustomPermissionEvaluator(
    private val authorizationBizService: AuthorizationBizService,
) : PermissionEvaluator {

    /**
     * 권한 코드로 권한 확인
     *
     * @param authentication 현재 인증 정보
     * @param targetDomainObject 대상 객체 (nullable — 권한 코드 방식에서는 사용하지 않는다)
     * @param permission 권한 코드 (예: "user:read")
     */
    override fun hasPermission(
        authentication: Authentication,
        targetDomainObject: Any?,
        permission: Any,
    ): Boolean {
        val user = authentication.principal as? AuthenticatedUser ?: return false
        val permissionCode = permission.toString()

        return authorizationBizService.hasPermission(user.userId, permissionCode)
    }

    /**
     * 리소스 타입과 권한으로 확인
     *
     * @param authentication 현재 인증 정보
     * @param targetId 대상 ID
     * @param targetType 리소스 타입 (예: "user")
     * @param permission 액션 (예: "read")
     */
    override fun hasPermission(
        authentication: Authentication,
        targetId: Serializable,
        targetType: String,
        permission: Any,
    ): Boolean {
        val user = authentication.principal as? AuthenticatedUser ?: return false
        val permissionCode = "$targetType:$permission"

        return authorizationBizService.hasPermission(user.userId, permissionCode)
    }
}