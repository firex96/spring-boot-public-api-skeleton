package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.dto.auth.UpdatePermissionRequestDto
import com.rex.api.pub.skeleton.exception.auth.AuthorizationException
import com.rex.api.pub.skeleton.exception.auth.DuplicateResourceException
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.GroupPermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.PermissionEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 권한 관리 — 조회·생성·수정·삭제. 시스템 권한·그룹에 할당된 권한 보호 규칙은 여기서 집행한다. */
@Service
class PermissionManagementBizService(
    private val permissionEntityService: PermissionEntityService,
    private val groupPermissionEntityService: GroupPermissionEntityService,
) {
    private val log = logger()

    fun getAllPermissions(): List<PermissionDto> = permissionEntityService.getAll()

    fun getPermissionById(id: Long): PermissionDto = requirePermission(id)

    fun getPermissionByCode(code: String): PermissionDto =
        permissionEntityService.getByCode(code) ?: throw ResourceNotFoundException("권한을 찾을 수 없습니다: $code")

    fun getPermissionsByResourceType(resourceType: String): List<PermissionDto> = permissionEntityService.getByResourceType(resourceType)

    @Transactional(transactionManager = "authTransactionManager")
    fun createPermission(request: CreatePermissionRequestDto): PermissionDto {
        if (permissionEntityService.existsByCode(request.code)) {
            throw DuplicateResourceException("이미 존재하는 권한 코드입니다: ${request.code}")
        }
        val (resourceType, action) = extractResourceAndAction(request.code)
        val saved =
            permissionEntityService.create(
                code = request.code,
                name = request.name,
                description = request.description,
                resourceType = request.resourceType ?: resourceType,
                action = request.action ?: action,
            )
        log.info("권한 생성됨: ${saved.code}")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updatePermission(id: Long, request: UpdatePermissionRequestDto): PermissionDto {
        val permission = requirePermission(id)
        if (permission.isSystem) {
            throw AuthorizationException("시스템 권한은 수정할 수 없습니다")
        }
        val saved = permissionEntityService.update(id = id, name = request.name, description = request.description)
        log.info("권한 수정됨: ${saved.code}")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun deletePermission(id: Long) {
        val permission = requirePermission(id)
        if (permission.isSystem) {
            throw AuthorizationException("시스템 권한은 삭제할 수 없습니다")
        }
        val assignedGroups = groupPermissionEntityService.countByPermissionId(id)
        if (assignedGroups > 0) {
            throw AuthorizationException("이 권한은 ${assignedGroups}개의 그룹에 할당되어 있습니다. 먼저 그룹에서 권한을 제거하세요.")
        }
        permissionEntityService.delete(id)
        log.info("권한 삭제됨: ${permission.code}")
    }

    private fun requirePermission(id: Long): PermissionDto =
        permissionEntityService.getById(id) ?: throw ResourceNotFoundException("권한을 찾을 수 없습니다: $id")

    /** `resource:action` 형식이면 둘로 나누고, 아니면 코드 전체를 리소스로 두고 액션은 `access`. */
    private fun extractResourceAndAction(code: String): Pair<String, String> {
        val parts = code.split(":")
        return if (parts.size == 2) parts[0] to parts[1] else code to "access"
    }
}