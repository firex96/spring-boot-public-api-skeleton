package com.rex.api.pub.skeleton.model.oidc

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenID Connect Discovery Model
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
    val responseTypesSupported: List<String> = listOf("code", "token", "id_token"),
    @field:JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String> = listOf("public"),
    @field:JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String> = listOf("RS256", "HS256"),
    @field:JsonProperty("scopes_supported")
    val scopesSupported: List<String> = listOf("openid", "profile", "email", "phone"),
    @field:JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = listOf("client_secret_basic", "client_secret_post"),
    @field:JsonProperty("claims_supported")
    val claimsSupported: List<String> = listOf(
        "sub", "name", "nickname", "picture", "email", "email_verified",
        "phone_number", "phone_number_verified", "updated_at",
    ),
)