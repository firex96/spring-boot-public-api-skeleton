package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.RoleDetailDto
import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.dto.auth.UpdateRoleRequestDto
import com.rex.api.pub.skeleton.exception.auth.AuthorizationException
import com.rex.api.pub.skeleton.exception.auth.DuplicateResourceException
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.GroupPermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.RoleEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹(= Role) 관리 — 조회·생성·수정·삭제. 시스템 그룹·기본 그룹 보호 규칙은 여기서 집행한다. */
@Service
class GroupManagementBizService(
    private val roleEntityService: RoleEntityService,
    private val userGroupEntityService: UserGroupEntityService,
    private val groupPermissionEntityService: GroupPermissionEntityService,
) {
    private val log = logger()

    fun getAllGroups(): List<RoleDto> = roleEntityService.getAll()

    fun getGroupById(id: Long): RoleDto = requireGroup(id)

    fun getGroupByCode(code: String): RoleDto =
        roleEntityService.getByCode(code) ?: throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $code")

    fun getGroupDetail(id: Long): RoleDetailDto {
        val role = requireGroup(id)
        val permissions = groupPermissionEntityService.getPermissionsByGroupId(id)
        val userCount = userGroupEntityService.getUserIdsByGroupId(id).size
        return RoleDetailDto(
            id = role.id,
            code = role.code,
            name = role.name,
            description = role.description,
            isSystem = role.isSystem,
            isDefault = role.isDefault,
            permissions = permissions,
            userCount = userCount,
            createdAt = role.createdAt,
            updatedAt = role.updatedAt,
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun createGroup(request: CreateRoleRequestDto): RoleDto {
        if (roleEntityService.existsByCode(request.code)) {
            throw DuplicateResourceException("이미 존재하는 그룹 코드입니다: ${request.code}")
        }
        // 기본 그룹은 하나만 — 새 그룹이 기본이면 기존 기본 그룹을 해제한다
        if (request.isDefault) {
            roleEntityService.getDefault()?.let { roleEntityService.clearDefault(it.id) }
        }
        val saved = roleEntityService.create(code = request.code, name = request.name, description = request.description, isDefault = request.isDefault)
        log.info("그룹 생성됨: ${saved.code}")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updateGroup(id: Long, request: UpdateRoleRequestDto): RoleDto {
        val role = requireGroup(id)
        if (role.isSystem) {
            throw AuthorizationException("시스템 그룹은 수정할 수 없습니다")
        }
        val isDefaultChange = request.isDefault != null && request.isDefault != role.isDefault
        if (isDefaultChange && request.isDefault == true) {
            roleEntityService.getDefault()?.let { existingDefault ->
                if (existingDefault.id != id) roleEntityService.clearDefault(existingDefault.id)
            }
        }
        val saved =
            roleEntityService.update(
                id = id,
                name = request.name,
                description = request.description,
                isDefault = if (isDefaultChange) request.isDefault else null,
            )
        log.info("그룹 수정됨: ${saved.code}")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun deleteGroup(id: Long) {
        val role = requireGroup(id)
        if (role.isSystem) {
            throw AuthorizationException("시스템 그룹은 삭제할 수 없습니다")
        }
        if (role.isDefault) {
            throw AuthorizationException("기본 그룹은 삭제할 수 없습니다. 먼저 다른 그룹을 기본 그룹으로 설정하세요.")
        }
        groupPermissionEntityService.removeAllByGroupId(id)
        roleEntityService.delete(id)
        log.info("그룹 삭제됨: ${role.code}")
    }

    private fun requireGroup(id: Long): RoleDto =
        roleEntityService.getById(id) ?: throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $id")
}