package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.dto.auth.UserRoleDto
import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserGroupEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.RoleRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 사용자-그룹 매핑 엔티티 서비스. `UserRoleDto`·그룹 코드 목록은 매핑 행과 Role 의 합성이라 Role 리포지토리도 같이 읽는다
 * (같은 auth 스키마·같은 트랜잭션 매니저).
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserGroupEntityService(
    private val userGroupRepository: UserGroupRepository,
    private val roleRepository: RoleRepository,
    private val mapper: AccessControlMapper,
) {
    /** 만료되지 않은 매핑의 그룹 코드 — 토큰·세션 캐시·권한 판정이 쓰는 값. */
    fun getActiveGroupCodesByUserId(userId: Long): List<String> {
        val groupIds = userGroupRepository.findActiveByUserId(userId).map { it.groupId }
        return if (groupIds.isEmpty()) emptyList() else roleRepository.findAllById(groupIds).map { it.code }
    }

    fun getActiveGroupsByUserId(userId: Long): List<RoleDto> {
        val groupIds = userGroupRepository.findActiveByUserId(userId).map { it.groupId }
        return if (groupIds.isEmpty()) emptyList() else mapper.toRoleDtoList(roleRepository.findAllById(groupIds))
    }

    fun getMappingsByUserId(userId: Long): List<UserRoleDto> {
        val userGroups = userGroupRepository.findByUserId(userId)
        val groupIds = userGroups.map { it.groupId }
        val groupsMap = if (groupIds.isEmpty()) emptyMap() else roleRepository.findAllById(groupIds).associateBy { it.id }
        return userGroups.mapNotNull { ug ->
            groupsMap[ug.groupId]?.let { group ->
                UserRoleDto(
                    userId = ug.userId,
                    roleId = ug.groupId,
                    roleCode = group.code,
                    roleName = group.name,
                    assignedAt = ug.assignedAt,
                    assignedBy = ug.assignedBy,
                    expiresAt = ug.expiresAt,
                )
            }
        }
    }

    fun getGroupIdsByUserId(userId: Long): List<Long> = userGroupRepository.findByUserId(userId).map { it.groupId }

    fun getUserIdsByGroupId(groupId: Long): List<Long> = userGroupRepository.findByGroupId(groupId).map { it.userId }

    fun existsByUserIdAndGroupId(userId: Long, groupId: Long): Boolean =
        userGroupRepository.existsByUserIdAndGroupId(userId, groupId)

    /** Role 부재는 프로그래밍 오류(NoSuchElementException) — 호출자가 먼저 검증한다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun assign(userId: Long, groupId: Long, assignedBy: Long?, expiresAt: LocalDateTime?): UserRoleDto {
        val role = roleRepository.findById(groupId).orElseThrow { NoSuchElementException("그룹이 없습니다: $groupId") }
        val saved = userGroupRepository.save(UserGroupEntity(userId = userId, groupId = groupId, assignedBy = assignedBy, expiresAt = expiresAt))
        return UserRoleDto(
            userId = saved.userId,
            roleId = saved.groupId,
            roleCode = role.code,
            roleName = role.name,
            assignedAt = saved.assignedAt,
            assignedBy = saved.assignedBy,
            expiresAt = saved.expiresAt,
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun remove(userId: Long, groupId: Long) {
        userGroupRepository.deleteByUserIdAndGroupId(userId, groupId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun removeAllByUserId(userId: Long) {
        userGroupRepository.deleteAll(userGroupRepository.findByUserId(userId))
    }
}