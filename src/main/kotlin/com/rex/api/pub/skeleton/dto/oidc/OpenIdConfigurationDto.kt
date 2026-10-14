package com.rex.api.pub.skeleton.dto.oidc

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenIdConfigurationDto(
    val issuer: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String,
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("revocation_endpoint")
    val revocationEndpoint: String,
    @JsonProperty("introspection_endpoint")
    val introspectionEndpoint: String,
    @JsonProperty("response_types_supported")
    val responseTypesSupported: List<String> = listOf("code"),
    @JsonProperty("grant_types_supported")
    val grantTypesSupported: List<String> = listOf(
        "authorization_code",
        "refresh_token",
        "client_credentials",
    ),
    @JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String> = listOf("public"),
    @JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String> = listOf("RS256"),
    @JsonProperty("scopes_supported")
    val scopesSupported: List<String> = listOf(
        "openid",
        "profile",
        "email",
        "phone",
        "offline_access",
    ),
    @JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = listOf(
        "client_secret_basic",
        "client_secret_post",
        "none",
    ),
    @JsonProperty("claims_supported")
    val claimsSupported: List<String> = listOf(
        "sub",
        "iss",
        "aud",
        "exp",
        "iat",
        "auth_time",
        "nonce",
        "name",
        "nickname",
        "picture",
        "email",
        "email_verified",
        "phone_number",
        "phone_number_verified",
    ),
    @JsonProperty("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String> = listOf("plain", "S256"),
)

// ===== UserInfo Endpoint =====