package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.security.BlockedIpDto
import com.rex.api.pub.skeleton.dto.security.LoginAttemptStatusDto
import com.rex.api.pub.skeleton.model.security.BlockedIpModel
import com.rex.api.pub.skeleton.model.security.LoginAttemptStatusModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.security.BlockedIpEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(config = MapstructConfiguration::class)
interface SecurityMapper {
    // ===== Entity ↔ DTO (EntityService에서 사용) =====

    // is-prefix Boolean: getter isActive() → 소스 프로퍼티명은 active, 생성자 파라미터는 isActive — 명시하지 않으면 미매핑으로 항상 false
    @Mapping(source = "active", target = "isActive")
    fun toDto(entity: BlockedIpEntity): BlockedIpDto

    fun toDtoList(entities: List<BlockedIpEntity>): List<BlockedIpDto>

    // blockedAt 은 DTO 가 non-null 로 들고 있으므로 그대로 옮긴다 — ignore 하면 no-arg 생성자가 초기화식을 실행하지 않아 null 로 저장 시도
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "unblockedAt", ignore = true)
    @Mapping(target = "unblockedBy", ignore = true)
    fun toEntity(dto: BlockedIpDto): BlockedIpEntity

    // ===== DTO ↔ Model (MappingService에서 사용) =====

    @Mapping(source = "active", target = "isActive")
    @Mapping(source = "permanent", target = "isPermanent")
    fun toModel(dto: BlockedIpDto): BlockedIpModel

    fun toModelList(dtoList: List<BlockedIpDto>): List<BlockedIpModel>

    @Mapping(source = "locked", target = "isLocked")
    fun toLoginAttemptStatusModel(dto: LoginAttemptStatusDto): LoginAttemptStatusModel
}