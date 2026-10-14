package com.rex.api.pub.skeleton.fixture

import com.rex.api.pub.skeleton.dto.auth.AssignGroupsToPermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.AssignPermissionsToGroupRequestDto
import com.rex.api.pub.skeleton.dto.auth.CreatePermissionRequestDto
import com.rex.api.pub.skeleton.dto.auth.GroupPermissionDto
import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.dto.auth.UpdatePermissionRequestDto
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.GroupPermissionEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PermissionEntity
import java.time.LocalDateTime

/**
 * 권한 관련 테스트 픽스처.
 *
 * 권한 관리 API 테스트에서 사용되는 요청/응답 객체와 엔티티를 생성합니다.
 *
 * 사용 예시:
 * ```kotlin
 * val createRequest = PermissionFixture.createRequest()
 * val permissionDto = PermissionFixture.permissionDto(code = "user:read")
 * ```
 */
object PermissionFixture {
    // 기본 권한 정보
    const val DEFAULT_CODE = "user:read"
    const val DEFAULT_NAME = "사용자 조회"
    const val DEFAULT_DESCRIPTION = "사용자 정보를 조회할 수 있는 권한"
    const val DEFAULT_RESOURCE_TYPE = "user"
    const val DEFAULT_ACTION = "read"

    /**
     * 권한 생성 요청 DTO.
     */
    fun createRequest(
        code: String = DEFAULT_CODE,
        name: String = DEFAULT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        resourceType: String? = DEFAULT_RESOURCE_TYPE,
        action: String? = DEFAULT_ACTION,
    ): CreatePermissionRequestDto =
        CreatePermissionRequestDto(
            code = code,
            name = name,
            description = description,
            resourceType = resourceType,
            action = action,
        )

    /**
     * 권한 수정 요청 DTO.
     */
    fun updateRequest(
        name: String = "수정된 권한 이름",
        description: String? = "수정된 권한 설명",
    ): UpdatePermissionRequestDto =
        UpdatePermissionRequestDto(
            name = name,
            description = description,
        )

    /**
     * 권한 DTO.
     */
    fun permissionDto(
        id: Long = 1,
        code: String = DEFAULT_CODE,
        name: String = DEFAULT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        resourceType: String? = DEFAULT_RESOURCE_TYPE,
        action: String? = DEFAULT_ACTION,
        isSystem: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): PermissionDto =
        PermissionDto(
            id = id,
            code = code,
            name = name,
            description = description,
            resourceType = resourceType,
            action = action,
            isSystem = isSystem,
            createdAt = createdAt,
        )

    /**
     * 권한 엔티티 생성 (DB 저장용).
     */
    fun permissionEntity(
        id: Long = 0,
        code: String = DEFAULT_CODE,
        name: String = DEFAULT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        resourceType: String? = DEFAULT_RESOURCE_TYPE,
        action: String? = DEFAULT_ACTION,
        isSystem: Boolean = false,
    ): PermissionEntity =
        PermissionEntity(
            id = id,
            code = code,
            name = name,
            description = description,
            resourceType = resourceType,
            action = action,
            isSystem = isSystem,
        )

    /**
     * 그룹에 권한 할당 요청 DTO.
     */
    fun assignPermissionsToGroupRequest(
        permissionIds: List<Long> = listOf(1, 2, 3),
    ): AssignPermissionsToGroupRequestDto =
        AssignPermissionsToGroupRequestDto(
            permissionIds = permissionIds,
        )

    /**
     * 권한에 그룹 할당 요청 DTO.
     */
    fun assignGroupsToPermissionRequest(
        groupIds: List<Long> = listOf(1, 2),
    ): AssignGroupsToPermissionRequestDto =
        AssignGroupsToPermissionRequestDto(
            groupIds = groupIds,
        )

    /**
     * 그룹-권한 매핑 DTO.
     */
    fun groupPermissionDto(
        groupId: Long = 1,
        permissionId: Long = 1,
        permissionCode: String = DEFAULT_CODE,
        permissionName: String = DEFAULT_NAME,
        grantedAt: LocalDateTime = LocalDateTime.now(),
        grantedBy: Long? = 1L,
    ): GroupPermissionDto =
        GroupPermissionDto(
            groupId = groupId,
            permissionId = permissionId,
            permissionCode = permissionCode,
            permissionName = permissionName,
            grantedAt = grantedAt,
            grantedBy = grantedBy,
        )

    /**
     * 그룹-권한 매핑 엔티티.
     */
    fun groupPermissionEntity(
        id: Long = 0,
        groupId: Long = 1,
        permissionId: Long = 1,
        grantedBy: Long? = 1L,
    ): GroupPermissionEntity =
        GroupPermissionEntity(
            id = id,
            groupId = groupId,
            permissionId = permissionId,
            grantedBy = grantedBy,
        )

    /**
     * 자주 사용되는 권한 코드 목록.
     */
    object Codes {
        const val USER_READ = "user:read"
        const val USER_WRITE = "user:write"
        const val USER_DELETE = "user:delete"
        const val ADMIN_READ = "admin:read"
        const val ADMIN_WRITE = "admin:write"
        const val SYSTEM_MANAGE = "system:manage"
    }

    /**
     * 잘못된 형식의 권한 요청 모음 (Validation 테스트용).
     */
    object Invalid {
        /** 빈 권한 코드 */
        fun createRequestWithEmptyCode() =
            CreatePermissionRequestDto(
                code = "",
                name = DEFAULT_NAME,
            )

        /** 잘못된 권한 코드 형식 */
        fun createRequestWithInvalidCode() =
            CreatePermissionRequestDto(
                code = "invalid_code_format",
                name = DEFAULT_NAME,
            )

        /** 빈 권한 이름 */
        fun createRequestWithEmptyName() =
            CreatePermissionRequestDto(
                code = DEFAULT_CODE,
                name = "",
            )

        /** 너무 긴 권한 코드 (100자 초과) */
        fun createRequestWithTooLongCode() =
            CreatePermissionRequestDto(
                code = "a".repeat(50) + ":" + "b".repeat(51),
                name = DEFAULT_NAME,
            )

        /** 빈 수정 요청 이름 */
        fun updateRequestWithEmptyName() =
            UpdatePermissionRequestDto(
                name = "",
            )
    }
}