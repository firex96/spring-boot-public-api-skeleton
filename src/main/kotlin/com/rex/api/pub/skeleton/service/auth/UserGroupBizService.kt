package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.dto.auth.UserRoleDto
import com.rex.api.pub.skeleton.exception.auth.DuplicateResourceException
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.RoleEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserGroupEntityService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 사용자 ↔ 그룹 매핑 — 배정·회수·동기화. 그룹이 바뀐 사용자의 세션 캐시는 즉시 지운다(다음 요청에서 새 그룹으로 재적재). */
@Service
class UserGroupBizService(
    private val userEntityService: UserEntityService,
    private val roleEntityService: RoleEntityService,
    private val userGroupEntityService: UserGroupEntityService,
    private val sessionCacheBizService: SessionCacheBizService,
) {
    private val log = logger()

    fun getGroupsByUserId(userId: Long): List<RoleDto> {
        requireUserExists(userId)
        return userGroupEntityService.getActiveGroupsByUserId(userId)
    }

    fun getUserGroupMappings(userId: Long): List<UserRoleDto> {
        requireUserExists(userId)
        return userGroupEntityService.getMappingsByUserId(userId)
    }

    fun getUsersByGroupId(groupId: Long): List<Long> {
        requireGroupExists(groupId)
        return userGroupEntityService.getUserIdsByGroupId(groupId)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun assignUserToGroup(userId: Long, groupId: Long, assignedBy: Long?, expiresAt: LocalDateTime? = null): UserRoleDto {
        requireUserExists(userId)
        val role = requireGroup(groupId)
        if (userGroupEntityService.existsByUserIdAndGroupId(userId, groupId)) {
            throw DuplicateResourceException("이미 할당된 그룹입니다")
        }
        val saved = userGroupEntityService.assign(userId, groupId, assignedBy, expiresAt)
        sessionCacheBizService.deleteAllUserSessions(userId)
        log.info("사용자 $userId 가 그룹 '${role.code}'에 할당됨")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun assignUsersToGroup(userIds: List<Long>, groupId: Long, assignedBy: Long?, expiresAt: LocalDateTime? = null): List<UserRoleDto> {
        val role = requireGroup(groupId)
        val existingUserIds = userEntityService.getExistingUserIds(userIds)
        val notFoundUserIds = userIds.filter { it !in existingUserIds }
        if (notFoundUserIds.isNotEmpty()) {
            throw ResourceNotFoundException("사용자를 찾을 수 없습니다: $notFoundUserIds")
        }
        val existingAssignments = userGroupEntityService.getUserIdsByGroupId(groupId).toSet()
        val newUserIds = userIds.filter { it !in existingAssignments }
        val saved = newUserIds.map { userGroupEntityService.assign(it, groupId, assignedBy, expiresAt) }
        newUserIds.forEach { sessionCacheBizService.deleteAllUserSessions(it) }
        log.info("${saved.size}명의 사용자가 그룹 '${role.code}'에 할당됨")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun assignGroupsToUser(userId: Long, groupIds: List<Long>, assignedBy: Long?, expiresAt: LocalDateTime? = null): List<UserRoleDto> {
        requireUserExists(userId)
        requireGroupsExist(groupIds)
        val existingAssignments = userGroupEntityService.getGroupIdsByUserId(userId).toSet()
        val saved = groupIds.filter { it !in existingAssignments }.map { userGroupEntityService.assign(userId, it, assignedBy, expiresAt) }
        sessionCacheBizService.deleteAllUserSessions(userId)
        log.info("사용자 $userId 에 ${saved.size}개 그룹이 할당됨")
        return saved
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun removeUserFromGroup(userId: Long, groupId: Long) {
        requireUserExists(userId)
        requireGroupExists(groupId)
        if (!userGroupEntityService.existsByUserIdAndGroupId(userId, groupId)) {
            throw ResourceNotFoundException("해당 그룹에 사용자가 할당되어 있지 않습니다")
        }
        userGroupEntityService.remove(userId, groupId)
        sessionCacheBizService.deleteAllUserSessions(userId)
        log.info("사용자 $userId 가 그룹 $groupId 에서 제거됨")
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun syncGroupsToUser(userId: Long, groupIds: List<Long>, assignedBy: Long?, expiresAt: LocalDateTime? = null): List<UserRoleDto> {
        requireUserExists(userId)
        userGroupEntityService.removeAllByUserId(userId)
        if (groupIds.isEmpty()) {
            sessionCacheBizService.deleteAllUserSessions(userId)
            return emptyList()
        }
        requireGroupsExist(groupIds)
        val saved = groupIds.map { userGroupEntityService.assign(userId, it, assignedBy, expiresAt) }
        sessionCacheBizService.deleteAllUserSessions(userId)
        log.info("사용자 $userId 의 그룹이 ${saved.size}개로 동기화됨")
        return saved
    }

    private fun requireUserExists(userId: Long) {
        if (!userEntityService.existsById(userId)) {
            throw ResourceNotFoundException("사용자를 찾을 수 없습니다: $userId")
        }
    }

    private fun requireGroup(groupId: Long): RoleDto =
        roleEntityService.getById(groupId) ?: throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $groupId")

    private fun requireGroupExists(groupId: Long) {
        if (!roleEntityService.existsById(groupId)) {
            throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $groupId")
        }
    }

    private fun requireGroupsExist(groupIds: List<Long>) {
        val foundIds = roleEntityService.getAllByIds(groupIds).map { it.id }
        val notFoundIds = groupIds.filter { it !in foundIds }
        if (notFoundIds.isNotEmpty()) {
            throw ResourceNotFoundException("그룹을 찾을 수 없습니다: $notFoundIds")
        }
    }
}