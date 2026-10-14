package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdatePermissionRequestDto
import com.rex.api.pub.skeleton.mapper.AccessControlMapper
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.service.auth.PermissionManagementBizService
import org.springframework.stereotype.Service

/** 관리자 권한 API 매핑 — `AdminPermissionController`. */
@Service
class PermissionMappingService(
    private val permissionManagementBizService: PermissionManagementBizService,
    private val mapper: AccessControlMapper,
) {
    fun getAllPermissions(): List<PermissionModel> = mapper.toPermissionModelList(permissionManagementBizService.getAllPermissions())

    fun getPermission(id: Long): PermissionModel = mapper.toPermissionModel(permissionManagementBizService.getPermissionById(id))

    fun getPermissionsByResourceType(resourceType: String): List<PermissionModel> =
        mapper.toPermissionModelList(permissionManagementBizService.getPermissionsByResourceType(resourceType))

    fun createPermission(request: CreatePermissionRequestDto): PermissionModel =
        mapper.toPermissionModel(permissionManagementBizService.createPermission(request))

    fun updatePermission(id: Long, request: UpdatePermissionRequestDto): PermissionModel =
        mapper.toPermissionModel(permissionManagementBizService.updatePermission(id, request))

    fun deletePermission(id: Long) {
        permissionManagementBizService.deletePermission(id)
    }
}