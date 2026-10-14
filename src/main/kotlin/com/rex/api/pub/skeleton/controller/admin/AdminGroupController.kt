package com.rex.api.pub.skeleton.controller.admin

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.auth.AssignPermissionsToGroupRequestDto
import com.rex.api.pub.skeleton.dto.auth.AssignUsersToRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdateRoleRequestDto
import com.rex.api.pub.skeleton.model.auth.GroupDetailModel
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.GroupPermissionModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.model.auth.UserGroupModel
import com.rex.api.pub.skeleton.service.mapping.GroupMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Groups", description = "그룹 관리 API")
@RestController
@RequestMapping("/admin/groups")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
class AdminGroupController(
    private val groupMappingService: GroupMappingService,
) {
    @Operation(summary = "전체 그룹 목록 조회")
    @GetMapping
    fun getAllGroups(): ResponseEntity<List<GroupModel>> {
        val groups = groupMappingService.getAllGroups()
        return ResponseEntity.ok(groups)
    }

    @Operation(summary = "그룹 상세 조회")
    @GetMapping("/{id}")
    fun getGroup(@PathVariable id: Long): ResponseEntity<GroupDetailModel> {
        val group = groupMappingService.getGroupDetail(id)
        return ResponseEntity.ok(group)
    }

    @Operation(summary = "그룹 생성")
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun createGroup(
        @Valid @RequestBody request: CreateRoleRequestDto,
    ): ResponseEntity<GroupModel> {
        val group = groupMappingService.createGroup(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(group)
    }

    @Operation(summary = "그룹 수정")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun updateGroup(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRoleRequestDto,
    ): ResponseEntity<GroupModel> {
        val group = groupMappingService.updateGroup(id, request)
        return ResponseEntity.ok(group)
    }

    @Operation(summary = "그룹 삭제")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun deleteGroup(@PathVariable id: Long): ResponseEntity<Void> {
        groupMappingService.deleteGroup(id)
        return ResponseEntity.noContent().build()
    }

    // 그룹-권한 관리

    @Operation(summary = "그룹의 권한 목록 조회")
    @GetMapping("/{id}/permissions")
    fun getGroupPermissions(@PathVariable id: Long): ResponseEntity<List<PermissionModel>> {
        val permissions = groupMappingService.getPermissionsByGroupId(id)
        return ResponseEntity.ok(permissions)
    }

    @Operation(summary = "그룹에 권한 할당")
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun assignPermissionsToGroup(
        @PathVariable id: Long,
        @Valid @RequestBody request: AssignPermissionsToGroupRequestDto,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<GroupPermissionModel>> {
        val permissions = groupMappingService.assignPermissionsToGroup(
            groupId = id,
            permissionIds = request.permissionIds,
            grantedBy = user.userId,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(permissions)
    }

    @Operation(summary = "그룹의 권한 동기화 (기존 권한 제거 후 새로 할당)")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun syncPermissionsToGroup(
        @PathVariable id: Long,
        @Valid @RequestBody request: AssignPermissionsToGroupRequestDto,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<GroupPermissionModel>> {
        val permissions = groupMappingService.syncPermissionsToGroup(
            groupId = id,
            permissionIds = request.permissionIds,
            grantedBy = user.userId,
        )
        return ResponseEntity.ok(permissions)
    }

    @Operation(summary = "그룹에서 권한 제거")
    @DeleteMapping("/{groupId}/permissions/{permissionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun removePermissionFromGroup(
        @PathVariable groupId: Long,
        @PathVariable permissionId: Long,
    ): ResponseEntity<Void> {
        groupMappingService.removePermissionFromGroup(groupId, permissionId)
        return ResponseEntity.noContent().build()
    }

    // 그룹-사용자 관리

    @Operation(summary = "그룹에 속한 사용자 목록 조회")
    @GetMapping("/{id}/users")
    fun getGroupUsers(@PathVariable id: Long): ResponseEntity<List<Long>> {
        val userIds = groupMappingService.getUsersByGroupId(id)
        return ResponseEntity.ok(userIds)
    }

    @Operation(summary = "그룹에 사용자 할당")
    @PreAuthorize("hasRole('SUPER_ADMIN')") // 그룹 배정은 권한 상승 경로 — ADMIN 은 자기 자신에게 SUPER_ADMIN 을 줄 수 없어야 한다
    @PostMapping("/{id}/users")
    fun assignUsersToGroup(
        @PathVariable id: Long,
        @Valid @RequestBody request: AssignUsersToRoleRequestDto,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<UserGroupModel>> {
        val userGroups = groupMappingService.assignUsersToGroup(
            userIds = request.userIds,
            groupId = id,
            assignedBy = user.userId,
            expiresAt = request.expiresAt,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(userGroups)
    }

    @Operation(summary = "그룹에서 사용자 제거")
    @PreAuthorize("hasRole('SUPER_ADMIN')") // 그룹 배정은 권한 상승 경로 — ADMIN 은 자기 자신에게 SUPER_ADMIN 을 줄 수 없어야 한다
    @DeleteMapping("/{groupId}/users/{userId}")
    fun removeUserFromGroup(
        @PathVariable groupId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        groupMappingService.removeUserFromGroup(userId, groupId)
        return ResponseEntity.noContent().build()
    }
}