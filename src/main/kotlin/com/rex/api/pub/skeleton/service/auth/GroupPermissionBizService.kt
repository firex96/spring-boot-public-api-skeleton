package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.GroupPermissionDto
import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.exception.auth.DuplicateResourceException
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.GroupPermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.PermissionEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.RoleEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹 ↔ 권한 매핑 — 부여·회수·동기화. */
@Service
class GroupPermissionBizService(
    private val roleEntityService: RoleEntityService,
    private val permissionEntityService: PermissionEntityService,
    private val groupPermissionEntityService: GroupPermissionEntityService,
) {
    private val log = logger()

    fun getPermissionsByGroupId(groupId: Long): List<PermissionDto> {
        requireGroupExists(groupId)
        return groupPermissionEntityService.getPermissionsByGroupId(groupId)
    }

    fun getGroupPermissionMappings(groupId: Long): List<GroupPermissionDto> {
        requireGroupExists(groupId)
        return groupPermissionEntityService.getMappingsByGroupId(groupId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun assignPermissionToGroup(groupId: Long, permissionId: Long, grantedBy: Long?): GroupPermissionDto {
        val role = requireGroup(groupId)
        val permission = permissionEntityService.getById(permissionId) ?: throw ResourceNotFoundException("권한을 찾을 수 없습니다: $permissionId")
        if (groupPermissionEntityService.existsByGroupIdAndPermissionId(groupId, permissionId)) {
            throw DuplicateResourceException("이미 할당된 권한입니다")
        }
        val saved = groupPermissionEntityService.assign(groupId, permissionId, grantedBy)
        log.info("권한 '${permission.code}'가 그룹 '${role.code}'에 할당됨")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun assignPermissionsToGroup(groupId: Long, permissionIds: List<Long>, grantedBy: Long?): List<GroupPermissionDto> {
        val role = requireGroup(groupId)
        requirePermissionsExist(permissionIds)
        val existingPermissionIds = groupPermissionEntityService.getPermissionIdsByGroupId(groupId).toSet()
        val saved = permissionIds.filter { it !in existingPermissionIds }.map { groupPermissionEntityService.assign(groupId, it, grantedBy) }
        log.info("${saved.size}개의 권한이 그룹 '${role.code}'에 할당됨")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun removePermissionFromGroup(groupId: Long, permissionId: Long) {
        requireGroupExists(groupId)
        if (!permissionEntityService.existsById(permissionId)) {
            throw ResourceNotFoundException("권한을 찾을 수 없습니다: $permissionId")
        }
        if (!groupPermissionEntityService.existsByGroupIdAndPermissionId(groupId, permissionId)) {
            throw ResourceNotFoundException("해당 권한이 그룹에 할당되어 있지 않습니다")
        }
        groupPermissionEntityService.remove(groupId, permissionId)
        log.info("권한 '$permissionId'가 그룹 '$groupId'에서 제거됨")
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun removeAllPermissionsFromGroup(groupId: Long) {
        requireGroupExists(groupId)
        groupPermissionEntityService.removeAllByGroupId(groupId)
        log.info("그룹 '$groupId'의 모든 권한이 제거됨")
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun syncPermissionsToGroup(groupId: Long, permissionIds: List<Long>, grantedBy: Long?): List<GroupPermissionDto> {
        val role = requireGroup(groupId)
        groupPermissionEntityService.removeAllByGroupId(groupId)
        if (permissionIds.isEmpty()) {
            log.info("그룹 '${role.code}'의 모든 권한이 동기화됨 (0개)")
            return emptyList()
        }
        requirePermissionsExist(permissionIds)
        val saved = permissionIds.map { groupPermissionEntityService.assign(groupId, it, grantedBy) }
        log.info("그룹 '${role.code}'의 권한이 ${saved.size}개로 동기화됨")
        return saved
    }

    private fun requireGroup(groupId: Long): RoleDto =
        roleEntityService.getById(groupId) ?: throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $groupId")

    private fun requireGroupExists(groupId: Long) {
        if (!roleEntityService.existsById(groupId)) {
            throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $groupId")
        }
    }

    private fun requirePermissionsExist(permissionIds: List<Long>) {
        val foundIds = permissionEntityService.getAllByIds(permissionIds).map { it.id }
        val notFoundIds = permissionIds.filter { it !in foundIds }
        if (notFoundIds.isNotEmpty()) {
            throw ResourceNotFoundException("권한을 찾을 수 없습니다: $notFoundIds")
        }
    }
}