package com.rex.api.pub.skeleton.controller.oidc

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.model.oidc.OpenIdConfigurationModel
import com.rex.api.pub.skeleton.model.oidc.UserInfoModel
import com.rex.api.pub.skeleton.service.mapping.OidcMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "OpenID Connect", description = "OIDC Discovery, JWKS, UserInfo 엔드포인트")
@RestController
class OidcController(
    private val mappingService: OidcMappingService,
) {
    @Operation(summary = "OpenID Connect Discovery", description = "OIDC 설정 정보 반환")
    @GetMapping("/.well-known/openid-configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getOpenIdConfiguration(): ResponseEntity<OpenIdConfigurationModel> =
        ResponseEntity.ok(mappingService.getOpenIdConfiguration())

    @Operation(summary = "JSON Web Key Set", description = "ID Token 검증을 위한 공개 키 반환")
    @GetMapping("/.well-known/jwks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getJwks(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mappingService.getJwkSet())

    @Operation(summary = "UserInfo 조회", description = "Access Token으로 사용자 정보 조회")
    @GetMapping("/oauth2/userinfo", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getUserInfo(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<UserInfoModel> =
        ResponseEntity.ok(mappingService.getUserInfo(user.userId))
}