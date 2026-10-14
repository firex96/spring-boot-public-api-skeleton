package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.model.auth.UserGroupModel
import com.rex.api.pub.skeleton.service.auth.AuthorizationBizService
import com.rex.api.pub.skeleton.service.auth.UserGroupBizService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 관리자 사용자 접근 제어 API 매핑 — `AdminUserController`(사용자의 그룹·권한·확인). */
@Service
class UserAccessMappingService(
    private val userGroupBizService: UserGroupBizService,
    private val authorizationBizService: AuthorizationBizService,
    private val mapper: AccessControlMapper,
) {
    fun getUserGroups(userId: Long): List<GroupModel> = mapper.toGroupModelList(userGroupBizService.getGroupsByUserId(userId))

    fun getUserGroupMappings(userId: Long): List<UserGroupModel> = mapper.toUserGroupModelList(userGroupBizService.getUserGroupMappings(userId))

    fun getUserPermissions(userId: Long): List<PermissionModel> = mapper.toPermissionModelList(authorizationBizService.getUserPermissions(userId))

    fun assignGroupsToUser(userId: Long, groupIds: List<Long>, assignedBy: Long?, expiresAt: LocalDateTime?): List<UserGroupModel> =
        mapper.toUserGroupModelList(userGroupBizService.assignGroupsToUser(userId, groupIds, assignedBy, expiresAt))

    fun syncGroupsToUser(userId: Long, groupIds: List<Long>, assignedBy: Long?, expiresAt: LocalDateTime?): List<UserGroupModel> =
        mapper.toUserGroupModelList(userGroupBizService.syncGroupsToUser(userId, groupIds, assignedBy, expiresAt))

    fun removeGroupFromUser(userId: Long, groupId: Long) {
        userGroupBizService.removeUserFromGroup(userId, groupId)
    }

    fun hasPermission(userId: Long, permissionCode: String): Boolean = authorizationBizService.hasPermission(userId, permissionCode)

    fun hasGroup(userId: Long, groupCode: String): Boolean = authorizationBizService.hasGroup(userId, groupCode)
}