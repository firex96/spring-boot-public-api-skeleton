package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.PermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service

@Service
class AuthorizationBizService(
    private val userGroupEntityService: UserGroupEntityService,
    private val permissionEntityService: PermissionEntityService,
    private val sessionCacheBizService: SessionCacheBizService,
) {
    private val log = logger()

    companion object {
        const val SUPER_ADMIN_GROUP = "SUPER_ADMIN"
    }

    /**
     * 사용자가 특정 권한을 가지고 있는지 확인합니다.
     */
    fun hasPermission(userId: Long, permissionCode: String): Boolean {
        // SUPER_ADMIN 그룹은 모든 권한을 가짐
        if (isSuperAdmin(userId)) {
            return true
        }

        val userPermissions = getUserPermissions(userId)
        return userPermissions.any { it.code == permissionCode }
    }

    /**
     * 사용자가 여러 권한 중 하나라도 가지고 있는지 확인합니다.
     */
    fun hasAnyPermission(userId: Long, permissionCodes: List<String>): Boolean {
        if (isSuperAdmin(userId)) {
            return true
        }

        val userPermissions = getUserPermissions(userId)
        val userPermissionCodes = userPermissions.map { it.code }.toSet()
        return permissionCodes.any { it in userPermissionCodes }
    }

    /**
     * 사용자가 모든 권한을 가지고 있는지 확인합니다.
     */
    fun hasAllPermissions(userId: Long, permissionCodes: List<String>): Boolean {
        if (isSuperAdmin(userId)) {
            return true
        }

        val userPermissions = getUserPermissions(userId)
        val userPermissionCodes = userPermissions.map { it.code }.toSet()
        return permissionCodes.all { it in userPermissionCodes }
    }

    /**
     * 사용자가 특정 그룹에 속해 있는지 확인합니다.
     */
    fun hasGroup(userId: Long, groupCode: String): Boolean {
        val userGroups = getUserGroups(userId)
        return userGroups.any { it == groupCode }
    }

    /**
     * 사용자가 여러 그룹 중 하나에라도 속해 있는지 확인합니다.
     */
    fun hasAnyGroup(userId: Long, groupCodes: List<String>): Boolean {
        val userGroups = getUserGroups(userId)
        return groupCodes.any { it in userGroups }
    }

    /**
     * 사용자가 SUPER_ADMIN 그룹에 속해 있는지 확인합니다.
     */
    fun isSuperAdmin(userId: Long): Boolean = hasGroup(userId, SUPER_ADMIN_GROUP)

    /**
     * 사용자가 ADMIN 그룹 이상에 속해 있는지 확인합니다.
     */
    fun isAdmin(userId: Long): Boolean = hasAnyGroup(userId, listOf(SUPER_ADMIN_GROUP, "ADMIN"))

    /**
     * 사용자의 모든 권한 코드 목록을 반환합니다.
     */
    fun getUserPermissionCodes(userId: Long): List<String> = getUserPermissions(userId).map { it.code }

    /**
     * 사용자의 모든 권한을 반환합니다.
     */
    fun getUserPermissions(userId: Long): List<PermissionDto> {
        // 세션 캐시를 먼저 본다 — 캐시가 비어 있으면 DB 세션으로 채우는 부수효과가 있어 호출을 유지한다(기존 동작)
        sessionCacheBizService.getSessionByUserId(userId)
        // 권한 정보는 캐시 유무와 무관하게 DB 에서 읽는다(기존 동작)
        return permissionEntityService.getAllByUserId(userId)
    }

    /**
     * 사용자의 모든 그룹 코드 목록을 반환합니다.
     */
    fun getUserGroups(userId: Long): List<String> {
        // 캐시에서 먼저 조회
        val cachedSession = sessionCacheBizService.getSessionByUserId(userId)
        if (cachedSession != null && cachedSession.groups.isNotEmpty()) {
            return cachedSession.groups
        }

        // DB에서 조회
        return userGroupEntityService.getActiveGroupCodesByUserId(userId)
    }

    /**
     * 특정 리소스 타입에 대한 사용자 권한을 확인합니다.
     */
    fun hasResourcePermission(userId: Long, resourceType: String, action: String): Boolean {
        val permissionCode = "$resourceType:$action"
        return hasPermission(userId, permissionCode)
    }

    /**
     * 특정 리소스에 대해 사용자가 수행할 수 있는 액션 목록을 반환합니다.
     */
    fun getAllowedActions(userId: Long, resourceType: String): List<String> {
        if (isSuperAdmin(userId)) {
            // SUPER_ADMIN은 모든 액션 허용
            return permissionEntityService.getByResourceType(resourceType).map { it.action ?: "access" }
        }

        val userPermissions = getUserPermissions(userId)
        return userPermissions
            .filter { it.resourceType == resourceType }
            .mapNotNull { it.action }
    }
}