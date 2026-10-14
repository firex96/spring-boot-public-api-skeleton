package com.rex.api.pub.skeleton.fixture

import com.rex.api.pub.skeleton.dto.auth.AssignRolesToUserRequestDto
import com.rex.api.pub.skeleton.dto.auth.AssignUsersToRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.CreateRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.dto.auth.RoleDetailDto
import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.dto.auth.UpdateRoleRequestDto
import com.rex.api.pub.skeleton.dto.auth.UserRoleDto
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.RoleEntity
import java.time.LocalDateTime

/**
 * 역할(그룹) 관련 테스트 픽스처.
 *
 * 역할 관리 API 테스트에서 사용되는 요청/응답 객체와 엔티티를 생성합니다.
 *
 * 사용 예시:
 * ```kotlin
 * val createRequest = RoleFixture.createRequest()
 * val roleDto = RoleFixture.roleDto(code = "ADMIN")
 * ```
 */
object RoleFixture {
    // 기본 역할 정보
    const val DEFAULT_CODE = "USER"
    const val DEFAULT_NAME = "일반 사용자"
    const val DEFAULT_DESCRIPTION = "기본 사용자 역할"

    /**
     * 역할 생성 요청 DTO.
     */
    fun createRequest(
        code: String = "NEW_ROLE",
        name: String = "새 역할",
        description: String? = "새 역할 설명",
        isDefault: Boolean = false,
    ): CreateRoleRequestDto =
        CreateRoleRequestDto(
            code = code,
            name = name,
            description = description,
            isDefault = isDefault,
        )

    /**
     * 역할 수정 요청 DTO.
     */
    fun updateRequest(
        name: String = "수정된 역할 이름",
        description: String? = "수정된 역할 설명",
        isDefault: Boolean? = null,
    ): UpdateRoleRequestDto =
        UpdateRoleRequestDto(
            name = name,
            description = description,
            isDefault = isDefault,
        )

    /**
     * 역할 DTO.
     */
    fun roleDto(
        id: Long = 1,
        code: String = DEFAULT_CODE,
        name: String = DEFAULT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        isSystem: Boolean = false,
        isDefault: Boolean = true,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
    ): RoleDto =
        RoleDto(
            id = id,
            code = code,
            name = name,
            description = description,
            isSystem = isSystem,
            isDefault = isDefault,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * 역할 상세 DTO (권한 포함).
     */
    fun roleDetailDto(
        id: Long = 1,
        code: String = DEFAULT_CODE,
        name: String = DEFAULT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        isSystem: Boolean = false,
        isDefault: Boolean = true,
        permissions: List<PermissionDto> = emptyList(),
        userCount: Int = 0,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
    ): RoleDetailDto =
        RoleDetailDto(
            id = id,
            code = code,
            name = name,
            description = description,
            isSystem = isSystem,
            isDefault = isDefault,
            permissions = permissions,
            userCount = userCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * 역할 엔티티 생성 (DB 저장용).
     */
    fun roleEntity(
        id: Long = 0,
        code: String = DEFAULT_CODE,
        name: String = DEFAULT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        isDefault: Boolean = true,
    ): RoleEntity =
        RoleEntity(
            code = code,
            name = name,
            description = description,
            isDefault = isDefault,
        ).also {
            if (id > 0) {
                val idField = RoleEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(it, id)
            }
        }

    /**
     * 역할에 사용자 할당 요청 DTO.
     */
    fun assignUsersToRoleRequest(
        userIds: List<Long> = listOf(1L, 2L, 3L),
        expiresAt: LocalDateTime? = null,
    ): AssignUsersToRoleRequestDto =
        AssignUsersToRoleRequestDto(
            userIds = userIds,
            expiresAt = expiresAt,
        )

    /**
     * 사용자에 역할 할당 요청 DTO.
     */
    fun assignRolesToUserRequest(
        roleIds: List<Long> = listOf(1, 2),
        expiresAt: LocalDateTime? = null,
    ): AssignRolesToUserRequestDto =
        AssignRolesToUserRequestDto(
            roleIds = roleIds,
            expiresAt = expiresAt,
        )

    /**
     * 사용자-역할 매핑 DTO.
     */
    fun userRoleDto(
        userId: Long = 1L,
        roleId: Long = 1,
        roleCode: String = DEFAULT_CODE,
        roleName: String = DEFAULT_NAME,
        assignedAt: LocalDateTime = LocalDateTime.now(),
        assignedBy: Long? = null,
        expiresAt: LocalDateTime? = null,
    ): UserRoleDto =
        UserRoleDto(
            userId = userId,
            roleId = roleId,
            roleCode = roleCode,
            roleName = roleName,
            assignedAt = assignedAt,
            assignedBy = assignedBy,
            expiresAt = expiresAt,
        )

    /**
     * 자주 사용되는 역할 코드.
     */
    object Codes {
        const val USER = "USER"
        const val ADMIN = "ADMIN"
        const val SUPER_ADMIN = "SUPER_ADMIN"
        const val MODERATOR = "MODERATOR"
        const val GUEST = "GUEST"
    }

    /**
     * 잘못된 형식의 역할 요청 모음 (Validation 테스트용).
     */
    object Invalid {
        /** 빈 역할 코드 */
        fun createRequestWithEmptyCode() =
            CreateRoleRequestDto(
                code = "",
                name = DEFAULT_NAME,
            )

        /** 잘못된 역할 코드 형식 (소문자) */
        fun createRequestWithLowercaseCode() =
            CreateRoleRequestDto(
                code = "lower_case",
                name = DEFAULT_NAME,
            )

        /** 빈 역할 이름 */
        fun createRequestWithEmptyName() =
            CreateRoleRequestDto(
                code = "VALID_CODE",
                name = "",
            )

        /** 너무 긴 역할 코드 (50자 초과) */
        fun createRequestWithTooLongCode() =
            CreateRoleRequestDto(
                code = "A".repeat(51),
                name = DEFAULT_NAME,
            )

        /** 빈 수정 요청 이름 */
        fun updateRequestWithEmptyName() =
            UpdateRoleRequestDto(
                name = "",
            )
    }
}