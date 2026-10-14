package com.rex.api.pub.skeleton.controller.admin

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.auth.AssignRolesToUserRequestDto
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.model.auth.UserGroupModel
import com.rex.api.pub.skeleton.service.mapping.UserAccessMappingService
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

@Tag(name = "Admin - Users", description = "사용자 권한 관리 API")
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
class AdminUserController(
    private val userAccessMappingService: UserAccessMappingService,
) {
    @Operation(summary = "사용자의 그룹 목록 조회")
    @GetMapping("/{userId}/groups")
    fun getUserGroups(@PathVariable userId: Long): ResponseEntity<List<GroupModel>> {
        val groups = userAccessMappingService.getUserGroups(userId)
        return ResponseEntity.ok(groups)
    }

    @Operation(summary = "사용자의 그룹 매핑 상세 조회")
    @GetMapping("/{userId}/groups/detail")
    fun getUserGroupMappings(@PathVariable userId: Long): ResponseEntity<List<UserGroupModel>> {
        val mappings = userAccessMappingService.getUserGroupMappings(userId)
        return ResponseEntity.ok(mappings)
    }

    @Operation(summary = "사용자의 권한 목록 조회")
    @GetMapping("/{userId}/permissions")
    fun getUserPermissions(@PathVariable userId: Long): ResponseEntity<List<PermissionModel>> {
        val permissions = userAccessMappingService.getUserPermissions(userId)
        return ResponseEntity.ok(permissions)
    }

    @Operation(summary = "사용자에게 그룹 할당")
    @PreAuthorize("hasRole('SUPER_ADMIN')") // 그룹 배정은 권한 상승 경로 — ADMIN 은 자기 자신에게 SUPER_ADMIN 을 줄 수 없어야 한다
    @PostMapping("/{userId}/groups")
    fun assignGroupsToUser(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AssignRolesToUserRequestDto,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<UserGroupModel>> {
        val userGroups = userAccessMappingService.assignGroupsToUser(
            userId = userId,
            groupIds = request.roleIds,
            assignedBy = user.userId,
            expiresAt = request.expiresAt,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(userGroups)
    }

    @Operation(summary = "사용자의 그룹 동기화 (기존 그룹 제거 후 새로 할당)")
    @PreAuthorize("hasRole('SUPER_ADMIN')") // 그룹 배정은 권한 상승 경로 — ADMIN 은 자기 자신에게 SUPER_ADMIN 을 줄 수 없어야 한다
    @PutMapping("/{userId}/groups")
    fun syncGroupsToUser(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AssignRolesToUserRequestDto,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<UserGroupModel>> {
        val userGroups = userAccessMappingService.syncGroupsToUser(
            userId = userId,
            groupIds = request.roleIds,
            assignedBy = user.userId,
            expiresAt = request.expiresAt,
        )
        return ResponseEntity.ok(userGroups)
    }

    @Operation(summary = "사용자에게서 그룹 제거")
    @PreAuthorize("hasRole('SUPER_ADMIN')") // 그룹 배정은 권한 상승 경로 — ADMIN 은 자기 자신에게 SUPER_ADMIN 을 줄 수 없어야 한다
    @DeleteMapping("/{userId}/groups/{groupId}")
    fun removeGroupFromUser(
        @PathVariable userId: Long,
        @PathVariable groupId: Long,
    ): ResponseEntity<Void> {
        userAccessMappingService.removeGroupFromUser(userId, groupId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자가 특정 권한을 가지고 있는지 확인")
    @GetMapping("/{userId}/permissions/{permissionCode}/check")
    fun checkUserPermission(
        @PathVariable userId: Long,
        @PathVariable permissionCode: String,
    ): ResponseEntity<Map<String, Boolean>> {
        val hasPermission = userAccessMappingService.hasPermission(userId, permissionCode)
        return ResponseEntity.ok(mapOf("hasPermission" to hasPermission))
    }

    @Operation(summary = "사용자가 특정 그룹에 속해 있는지 확인")
    @GetMapping("/{userId}/groups/{groupCode}/check")
    fun checkUserGroup(
        @PathVariable userId: Long,
        @PathVariable groupCode: String,
    ): ResponseEntity<Map<String, Boolean>> {
        val hasGroup = userAccessMappingService.hasGroup(userId, groupCode)
        return ResponseEntity.ok(mapOf("hasGroup" to hasGroup))
    }
}