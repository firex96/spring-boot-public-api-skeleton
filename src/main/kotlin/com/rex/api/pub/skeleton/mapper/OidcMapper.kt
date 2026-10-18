package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.oidc.UserInfoDto
import com.rex.api.pub.skeleton.model.oidc.UserInfoModel
import org.mapstruct.Mapper

@Mapper(config = MapstructConfiguration::class)
interface OidcMapper {
    // ===== DTO ↔ Model (MappingService에서 사용) =====

    fun toUserInfoModel(dto: UserInfoDto): UserInfoModel
}