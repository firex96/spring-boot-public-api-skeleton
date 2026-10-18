package com.rex.api.pub.skeleton.model.oidc

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenID Connect Discovery — 값은 전부 `OidcMappingService` 가 실제 지원값 상수
 * (`OAuth2AuthorizationBizService.SUPPORTED_*`, `IdTokenBizService.SUPPORTED_*`)로 채운다. 기본값을 두지 않는다.
 */
data class OpenIdConfigurationModel(
    @field:JsonProperty("issuer")
    val issuer: String,
    @field:JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @field:JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @field:JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String,
    @field:JsonProperty("jwks_uri")
    val jwksUri: String,
    @field:JsonProperty("revocation_endpoint")
    val revocationEndpoint: String,
    @field:JsonProperty("introspection_endpoint")
    val introspectionEndpoint: String,
    @field:JsonProperty("response_types_supported")
    val responseTypesSupported: List<String>,
    @field:JsonProperty("grant_types_supported")
    val grantTypesSupported: List<String>,
    @field:JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String>,
    @field:JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,
    @field:JsonProperty("scopes_supported")
    val scopesSupported: List<String>,
    @field:JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>,
    @field:JsonProperty("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>,
    @field:JsonProperty("claims_supported")
    val claimsSupported: List<String>,
)