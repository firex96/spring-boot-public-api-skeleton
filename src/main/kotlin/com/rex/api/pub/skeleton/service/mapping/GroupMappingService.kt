package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdateRoleRequestDto
import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.model.auth.GroupDetailModel
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.GroupPermissionModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.model.auth.UserGroupModel
import com.rex.api.pub.skeleton.service.auth.GroupManagementBizService
import com.rex.api.pub.skeleton.service.auth.GroupPermissionBizService
import com.rex.api.pub.skeleton.service.auth.UserGroupBizService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 관리자 그룹 API 매핑 — `AdminGroupController`(그룹 CRUD · 그룹-권한 · 그룹-사용자). */
@Service
class GroupMappingService(
    private val groupManagementBizService: GroupManagementBizService,
    private val groupPermissionBizService: GroupPermissionBizService,
    private val userGroupBizService: UserGroupBizService,
    private val mapper: AccessControlMapper,
) {
    fun getAllGroups(): List<GroupModel> = mapper.toGroupModelList(groupManagementBizService.getAllGroups())

    fun getGroupDetail(id: Long): GroupDetailModel = mapper.toGroupDetailModel(groupManagementBizService.getGroupDetail(id))

    fun createGroup(request: CreateRoleRequestDto): GroupModel = mapper.toGroupModel(groupManagementBizService.createGroup(request))

    fun updateGroup(id: Long, request: UpdateRoleRequestDto): GroupModel = mapper.toGroupModel(groupManagementBizService.updateGroup(id, request))

    fun deleteGroup(id: Long) {
        groupManagementBizService.deleteGroup(id)
    }

    fun getPermissionsByGroupId(groupId: Long): List<PermissionModel> =
        mapper.toPermissionModelList(groupPermissionBizService.getPermissionsByGroupId(groupId))

    fun assignPermissionsToGroup(groupId: Long, permissionIds: List<Long>, grantedBy: Long?): List<GroupPermissionModel> =
        mapper.toGroupPermissionModelList(groupPermissionBizService.assignPermissionsToGroup(groupId, permissionIds, grantedBy))

    fun syncPermissionsToGroup(groupId: Long, permissionIds: List<Long>, grantedBy: Long?): List<GroupPermissionModel> =
        mapper.toGroupPermissionModelList(groupPermissionBizService.syncPermissionsToGroup(groupId, permissionIds, grantedBy))

    fun removePermissionFromGroup(groupId: Long, permissionId: Long) {
        groupPermissionBizService.removePermissionFromGroup(groupId, permissionId)
    }

    fun getUsersByGroupId(groupId: Long): List<Long> = userGroupBizService.getUsersByGroupId(groupId)

    fun assignUsersToGroup(userIds: List<Long>, groupId: Long, assignedBy: Long?, expiresAt: LocalDateTime?): List<UserGroupModel> =
        mapper.toUserGroupModelList(userGroupBizService.assignUsersToGroup(userIds, groupId, assignedBy, expiresAt))

    fun removeUserFromGroup(userId: Long, groupId: Long) {
        userGroupBizService.removeUserFromGroup(userId, groupId)
    }
}