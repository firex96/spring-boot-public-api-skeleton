package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.auth.GroupPermissionDto
import com.rex.api.pub.skeleton.dto.auth.PermissionDto
import com.rex.api.pub.skeleton.dto.auth.RoleDetailDto
import com.rex.api.pub.skeleton.dto.auth.RoleDto
import com.rex.api.pub.skeleton.dto.auth.UserRoleDto
import com.rex.api.pub.skeleton.model.auth.GroupDetailModel
import com.rex.api.pub.skeleton.model.auth.GroupModel
import com.rex.api.pub.skeleton.model.auth.GroupPermissionModel
import com.rex.api.pub.skeleton.model.auth.PermissionModel
import com.rex.api.pub.skeleton.model.auth.UserGroupModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PermissionEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.RoleEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

/**
 * 접근 제어(그룹=Role · 권한 · 매핑) 변환. `UserRoleDto`·`GroupPermissionDto` 는 두 Entity 의 합성이라
 * EntityService 가 생성자로 만들고, 여기서는 단일 Entity ↔ DTO 와 DTO ↔ Model 만 맡는다.
 */
@Mapper(config = MapstructConfiguration::class)
interface AccessControlMapper {
    // ===== Entity ↔ DTO (EntityService에서 사용) =====

    // is-prefix Boolean: getter isSystem()/isDefault() → 소스 프로퍼티명 system/default, 생성자 파라미터 isSystem/isDefault
    @Mapping(source = "system", target = "isSystem")
    @Mapping(source = "default", target = "isDefault")
    fun toRoleDto(entity: RoleEntity): RoleDto

    fun toRoleDtoList(entities: List<RoleEntity>): List<RoleDto>

    @Mapping(source = "system", target = "isSystem")
    fun toPermissionDto(entity: PermissionEntity): PermissionDto

    fun toPermissionDtoList(entities: List<PermissionEntity>): List<PermissionDto>

    // ===== DTO ↔ Model (MappingService에서 사용) =====

    @Mapping(source = "system", target = "isSystem")
    @Mapping(source = "default", target = "isDefault")
    fun toGroupModel(dto: RoleDto): GroupModel

    fun toGroupModelList(dtoList: List<RoleDto>): List<GroupModel>

    @Mapping(source = "system", target = "isSystem")
    @Mapping(source = "default", target = "isDefault")
    fun toGroupDetailModel(dto: RoleDetailDto): GroupDetailModel

    @Mapping(source = "system", target = "isSystem")
    fun toPermissionModel(dto: PermissionDto): PermissionModel

    fun toPermissionModelList(dtoList: List<PermissionDto>): List<PermissionModel>

    fun toGroupPermissionModel(dto: GroupPermissionDto): GroupPermissionModel

    fun toGroupPermissionModelList(dtoList: List<GroupPermissionDto>): List<GroupPermissionModel>

    fun toUserGroupModel(dto: UserRoleDto): UserGroupModel

    fun toUserGroupModelList(dtoList: List<UserRoleDto>): List<UserGroupModel>
}