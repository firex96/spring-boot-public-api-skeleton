package com.rex.api.pub.skeleton.mapper

import com.rex.api.pub.skeleton.config.MapstructConfiguration
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationConsentDto
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizeDecisionDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2AccessTokenDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2AuthorizationCodeDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientWithSecretDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2RefreshTokenDto
import com.rex.api.pub.skeleton.dto.oauth2.TokenIntrospectionResponseDto
import com.rex.api.pub.skeleton.dto.oidc.OidcTokenResponseDto
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.model.oauth2.AuthorizationConsentModel
import com.rex.api.pub.skeleton.model.oauth2.AuthorizeDecisionModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ClientWithSecretModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oauth2.OidcTokenResponseModel
import com.rex.api.pub.skeleton.model.oauth2.TokenIntrospectionResponseModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AccessTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2AuthorizationCodeEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2ClientEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2RefreshTokenEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named

/** OAuth2 클라이언트·토큰 변환. CSV 컬럼(`grantTypes`·`redirectUris`·`scopes`)은 `splitCsv` 로 목록이 된다. */
@Mapper(config = MapstructConfiguration::class)
interface OAuth2Mapper {
    // ===== Entity ↔ DTO (EntityService에서 사용) =====

    // is-prefix Boolean: getter isActive()/isFirstParty() → 소스 프로퍼티명 active/firstParty, 생성자 파라미터 isActive/isFirstParty
    @Mapping(source = "active", target = "isActive")
    @Mapping(source = "firstParty", target = "isFirstParty")
    @Mapping(source = "grantTypes", target = "grantTypes", qualifiedByName = ["splitCsv"])
    @Mapping(source = "redirectUris", target = "redirectUris", qualifiedByName = ["splitCsv"])
    @Mapping(source = "scopes", target = "scopes", qualifiedByName = ["splitCsv"])
    fun toClientDto(entity: OAuth2ClientEntity): OAuth2ClientDto

    fun toClientDtoList(entities: List<OAuth2ClientEntity>): List<OAuth2ClientDto>

    @Mapping(source = "used", target = "isUsed")
    fun toAuthorizationCodeDto(entity: OAuth2AuthorizationCodeEntity): OAuth2AuthorizationCodeDto

    @Mapping(source = "revoked", target = "isRevoked")
    fun toAccessTokenDto(entity: OAuth2AccessTokenEntity): OAuth2AccessTokenDto

    @Mapping(source = "revoked", target = "isRevoked")
    fun toRefreshTokenDto(entity: OAuth2RefreshTokenEntity): OAuth2RefreshTokenDto

    @Named("splitCsv")
    fun splitCsv(value: String?): List<String> = value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    // ===== DTO ↔ Model (MappingService에서 사용) =====

    @Mapping(source = "active", target = "isActive")
    @Mapping(source = "firstParty", target = "isFirstParty")
    fun toClientModel(dto: OAuth2ClientDto): OAuth2ClientModel

    fun toClientModelList(dtoList: List<OAuth2ClientDto>): List<OAuth2ClientModel>

    @Mapping(source = "active", target = "isActive")
    @Mapping(source = "firstParty", target = "isFirstParty")
    fun toClientWithSecretModel(dto: OAuth2ClientWithSecretDto): OAuth2ClientWithSecretModel

    fun toAuthorizationConsentModel(dto: AuthorizationConsentDto): AuthorizationConsentModel

    fun toOidcTokenResponseModel(dto: OidcTokenResponseDto): OidcTokenResponseModel

    fun toTokenIntrospectionResponseModel(dto: TokenIntrospectionResponseDto): TokenIntrospectionResponseModel

    fun toErrorResponseModel(e: OAuth2Exception): OAuth2ErrorResponseModel

    fun toAuthorizeDecisionModel(dto: AuthorizeDecisionDto): AuthorizeDecisionModel
}