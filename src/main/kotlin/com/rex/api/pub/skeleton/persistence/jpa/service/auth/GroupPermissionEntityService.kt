package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.GroupPermissionDto
import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.GroupPermissionEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.GroupPermissionRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.PermissionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹-권한 매핑 엔티티 서비스. `GroupPermissionDto` 는 매핑 행과 권한의 합성이라 권한 리포지토리도 같이 읽는다. */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class GroupPermissionEntityService(
    private val groupPermissionRepository: GroupPermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val mapper: AccessControlMapper,
) {
    fun getPermissionsByGroupId(groupId: Long): List<PermissionDto> {
        val permissionIds = groupPermissionRepository.findByGroupId(groupId).map { it.permissionId }
        return if (permissionIds.isEmpty()) emptyList() else mapper.toPermissionDtoList(permissionRepository.findAllById(permissionIds))
    }

    fun getMappingsByGroupId(groupId: Long): List<GroupPermissionDto> {
        val groupPermissions = groupPermissionRepository.findByGroupId(groupId)
        val permissionIds = groupPermissions.map { it.permissionId }
        val permissionsMap = if (permissionIds.isEmpty()) emptyMap() else permissionRepository.findAllById(permissionIds).associateBy { it.id }
        return groupPermissions.mapNotNull { gp ->
            permissionsMap[gp.permissionId]?.let { permission ->
                GroupPermissionDto(
                    groupId = gp.groupId,
                    permissionId = gp.permissionId,
                    permissionCode = permission.code,
                    permissionName = permission.name,
                    grantedAt = gp.grantedAt,
                    grantedBy = gp.grantedBy,
                )
            }
        }
    }

    fun getPermissionIdsByGroupId(groupId: Long): List<Long> = groupPermissionRepository.findByGroupId(groupId).map { it.permissionId }

    fun countByPermissionId(permissionId: Long): Int = groupPermissionRepository.findByPermissionId(permissionId).size

    fun existsByGroupIdAndPermissionId(groupId: Long, permissionId: Long): Boolean =
        groupPermissionRepository.existsByGroupIdAndPermissionId(groupId, permissionId)

    /** 권한 부재는 프로그래밍 오류(NoSuchElementException) — 호출자가 먼저 검증한다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun assign(groupId: Long, permissionId: Long, grantedBy: Long?): GroupPermissionDto {
        val permission = permissionRepository.findById(permissionId).orElseThrow { NoSuchElementException("권한이 없습니다: $permissionId") }
        val saved = groupPermissionRepository.save(GroupPermissionEntity(groupId = groupId, permissionId = permissionId, grantedBy = grantedBy))
        return GroupPermissionDto(
            groupId = saved.groupId,
            permissionId = saved.permissionId,
            permissionCode = permission.code,
            permissionName = permission.name,
            grantedAt = saved.grantedAt,
            grantedBy = saved.grantedBy,
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun remove(groupId: Long, permissionId: Long) {
        groupPermissionRepository.deleteByGroupIdAndPermissionId(groupId, permissionId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun removeAllByGroupId(groupId: Long) {
        groupPermissionRepository.deleteByGroupId(groupId)
    }
}