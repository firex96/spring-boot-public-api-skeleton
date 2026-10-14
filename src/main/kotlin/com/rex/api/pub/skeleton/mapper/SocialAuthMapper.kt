package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.auth.SocialAccountDto
import com.rex.api.pub.skeleton.dto.auth.UserSocialAccountDto
import com.rex.api.pub.skeleton.model.auth.SocialAccountModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSocialAccountEntity
import org.mapstruct.Mapper

/** 소셜 연동 계정 변환 — Entity → 내부 DTO(토큰 포함), 응답 DTO → Model. */
@Mapper(config = MapstructConfiguration::class)
interface SocialAuthMapper {
    // ===== Entity ↔ DTO (EntityService에서 사용) =====

    fun toUserSocialAccountDto(entity: UserSocialAccountEntity): UserSocialAccountDto

    fun toUserSocialAccountDtoList(entities: List<UserSocialAccountEntity>): List<UserSocialAccountDto>

    // ===== DTO ↔ Model (MappingService에서 사용) =====

    fun toSocialAccountModel(dto: SocialAccountDto): SocialAccountModel

    fun toSocialAccountModelList(dtoList: List<SocialAccountDto>): List<SocialAccountModel>
}