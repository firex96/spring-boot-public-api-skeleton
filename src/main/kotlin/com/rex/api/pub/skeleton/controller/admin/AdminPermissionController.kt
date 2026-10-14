package com.rex.api.pub.skeleton.controller.admin

import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.UpdatePermissionRequestDto
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.service.mapping.PermissionMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Permissions", description = "권한 관리 API")
@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AdminPermissionController(
    private val permissionMappingService: PermissionMappingService,
) {
    @Operation(summary = "전체 권한 목록 조회")
    @GetMapping
    fun getAllPermissions(): ResponseEntity<List<PermissionModel>> {
        val permissions = permissionMappingService.getAllPermissions()
        return ResponseEntity.ok(permissions)
    }

    @Operation(summary = "권한 상세 조회")
    @GetMapping("/{id}")
    fun getPermission(@PathVariable id: Long): ResponseEntity<PermissionModel> {
        val permission = permissionMappingService.getPermission(id)
        return ResponseEntity.ok(permission)
    }

    @Operation(summary = "리소스 타입별 권한 조회")
    @GetMapping("/by-resource")
    fun getPermissionsByResourceType(
        @RequestParam resourceType: String,
    ): ResponseEntity<List<PermissionModel>> {
        val permissions = permissionMappingService.getPermissionsByResourceType(resourceType)
        return ResponseEntity.ok(permissions)
    }

    @Operation(summary = "권한 생성")
    @PostMapping
    fun createPermission(
        @Valid @RequestBody request: CreatePermissionRequestDto,
    ): ResponseEntity<PermissionModel> {
        val permission = permissionMappingService.createPermission(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(permission)
    }

    @Operation(summary = "권한 수정")
    @PutMapping("/{id}")
    fun updatePermission(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePermissionRequestDto,
    ): ResponseEntity<PermissionModel> {
        val permission = permissionMappingService.updatePermission(id, request)
        return ResponseEntity.ok(permission)
    }

    @Operation(summary = "권한 삭제")
    @DeleteMapping("/{id}")
    fun deletePermission(@PathVariable id: Long): ResponseEntity<Void> {
        permissionMappingService.deletePermission(id)
        return ResponseEntity.noContent().build()
    }
}