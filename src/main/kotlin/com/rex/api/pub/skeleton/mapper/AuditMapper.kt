package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.audit.AuditLogDto
import com.rex.api.pub.skeleton.model.audit.AuditLogModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.audit.AuditLogEntity
import org.mapstruct.Mapper

@Mapper(config = MapstructConfiguration::class)
interface AuditMapper {
    // ===== Entity ↔ DTO (EntityService에서 사용) =====

    fun toDto(entity: AuditLogEntity): AuditLogDto

    fun toDtoList(entities: List<AuditLogEntity>): List<AuditLogDto>

    fun toEntity(dto: AuditLogDto): AuditLogEntity

    // ===== DTO ↔ Model (MappingService에서 사용) =====

    fun toModel(dto: AuditLogDto): AuditLogModel

    fun toModelList(dtoList: List<AuditLogDto>): List<AuditLogModel>
}