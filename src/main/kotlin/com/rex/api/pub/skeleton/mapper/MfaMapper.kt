package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.auth.MfaChallengeDto
import com.rex.api.pub.skeleton.dto.auth.MfaSetupDto
import com.rex.api.pub.skeleton.dto.auth.MfaStatusDto
import com.rex.api.pub.skeleton.dto.auth.UserMfaCredentialDto
import com.rex.api.pub.skeleton.model.auth.MfaChallengeModel
import com.rex.api.pub.skeleton.model.auth.MfaSetupModel
import com.rex.api.pub.skeleton.model.auth.MfaStatusModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserMfaCredentialEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

/** 2차 인증 — Entity ↔ DTO(EntityService), DTO ↔ Model(MappingService). */
@Mapper(config = MapstructConfiguration::class)
interface MfaMapper {
    fun toUserMfaCredentialDto(entity: UserMfaCredentialEntity): UserMfaCredentialDto

    @Mapping(source = "token", target = "mfaToken")
    fun toMfaChallengeModel(dto: MfaChallengeDto): MfaChallengeModel

    fun toMfaSetupModel(dto: MfaSetupDto): MfaSetupModel

    fun toMfaStatusModel(dto: MfaStatusDto): MfaStatusModel
}