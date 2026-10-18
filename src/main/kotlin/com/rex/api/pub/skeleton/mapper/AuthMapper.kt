package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.auth.AccessTokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.EmailVerificationTokenDto
import com.rex.api.pub.skeleton.dto.auth.PasswordResetTokenDto
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserInfoDto
import com.rex.api.pub.skeleton.dto.auth.UserSessionRecordDto
import com.rex.api.pub.skeleton.dto.user.UserProfileDto
import com.rex.api.pub.skeleton.dto.user.UserSessionDto
import com.rex.api.pub.skeleton.model.auth.AccessTokenResponseModel
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.model.auth.BrowserPrincipalModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.model.user.UserProfileModel
import com.rex.api.pub.skeleton.model.user.UserSessionModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.EmailVerificationTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PasswordResetTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserSessionEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named

@Mapper(config = MapstructConfiguration::class)
interface AuthMapper {

    // ===== User Entity ↔ DTO (EntityService에서 사용) =====

    /** [emailVerified] 는 호출자가 인증 기록(`TokenEntityService.existsVerifiedEmail`)으로 정한다 — 이메일 유무로 추정하지 않는다. */
    @Mapping(target = "emailVerified", source = "emailVerified")
    fun toUserProfileDto(entity: UserEntity, emailVerified: Boolean): UserProfileDto

    /** 내부 DTO — 비밀번호 해시·상태 포함. BizService 까지만 올라간다. */
    fun toUserDto(entity: UserEntity): UserDto

    // is-prefix Boolean: getter isRevoked() → 소스 프로퍼티명 revoked, 생성자 파라미터 isRevoked
    @Mapping(source = "revoked", target = "isRevoked")
    fun toUserSessionRecordDto(entity: UserSessionEntity): UserSessionRecordDto

    @Mapping(source = "used", target = "isUsed")
    fun toPasswordResetTokenDto(entity: PasswordResetTokenEntity): PasswordResetTokenDto

    @Mapping(source = "verified", target = "isVerified")
    fun toEmailVerificationTokenDto(entity: EmailVerificationTokenEntity): EmailVerificationTokenDto

    // ===== UserSession Entity ↔ DTO (EntityService에서 사용) =====

    @Mapping(target = "sessionId", source = "entity", qualifiedByName = ["maskSessionId"])
    @Mapping(target = "isCurrentSession", expression = "java(entity.getSessionId().equals(currentSessionId))")
    fun toUserSessionDto(entity: UserSessionEntity, currentSessionId: String?): UserSessionDto
    // toUserSessionDtoList 는 두지 않는다 — 자동 생성되는 요소 매핑은 currentSessionId 를 몰라 sessionId 마스킹·isCurrentSession 이 빠진다(ERROR 정책이 드러냄). 목록은 호출부가 toUserSessionDto 로 map 한다.

    @Named("maskSessionId")
    fun maskSessionId(entity: UserSessionEntity): String =
        entity.sessionId.take(8) + "****"

    // ===== DTO ↔ Model (MappingService에서 사용) =====

    fun toUserProfileModel(dto: UserProfileDto): UserProfileModel

    // is-prefix Boolean: getter isCurrentSession() → 소스 프로퍼티명 currentSession, 생성자 파라미터 isCurrentSession
    @Mapping(source = "currentSession", target = "isCurrentSession")
    fun toUserSessionModel(dto: UserSessionDto): UserSessionModel

    fun toUserSessionModelList(dtoList: List<UserSessionDto>): List<UserSessionModel>

    fun toAuthUserInfoModel(dto: UserInfoDto): AuthUserInfoModel

    fun toTokenResponseModel(dto: TokenResponseDto): TokenResponseModel

    fun toBrowserPrincipalModel(sessionInfo: SessionInfo): BrowserPrincipalModel

    fun toAccessTokenResponseModel(dto: AccessTokenResponseDto): AccessTokenResponseModel
}