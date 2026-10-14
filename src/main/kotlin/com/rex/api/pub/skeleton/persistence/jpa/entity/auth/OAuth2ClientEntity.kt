package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * OAuth2 클라이언트 (애플리케이션) 정보
 *
 * 이 서버를 Authorization Server로 사용하는 클라이언트 애플리케이션 등록 정보
 */
@Entity
@Table(name = "oauth2_clients")
class OAuth2ClientEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    var clientId: String,

    @Column(name = "client_secret", nullable = false, length = 255)
    var clientSecret: String,

    @Column(name = "client_name", nullable = false, length = 100)
    var clientName: String,

    @Column(name = "description", length = 500)
    var description: String? = null,

    // 허용된 grant types (콤마로 구분: authorization_code,refresh_token,client_credentials)
    @Column(name = "grant_types", nullable = false, length = 200)
    var grantTypes: String = "authorization_code,refresh_token",

    // 허용된 redirect URIs (콤마로 구분)
    @Column(name = "redirect_uris", length = 2000)
    var redirectUris: String? = null,

    // 허용된 scopes (콤마로 구분)
    @Column(name = "scopes", length = 500)
    var scopes: String = "openid,profile,email",

    // Access Token 유효 시간 (초)
    @Column(name = "access_token_validity", nullable = false)
    var accessTokenValidity: Int = 3600,

    // Refresh Token 유효 시간 (초)
    @Column(name = "refresh_token_validity", nullable = false)
    var refreshTokenValidity: Int = 604800,

    // 클라이언트 활성화 여부
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    // 1st party 앱 여부 (자체 앱은 consent 화면 스킵 가능)
    @Column(name = "is_first_party", nullable = false)
    var isFirstParty: Boolean = false,

    // PKCE 필수 여부
    @Column(name = "require_pkce", nullable = false)
    var requirePkce: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun getGrantTypeList(): List<String> = grantTypes.split(",").map { it.trim() }

    fun getRedirectUriList(): List<String> = redirectUris?.split(",")?.map { it.trim() } ?: emptyList()

    fun getScopeList(): List<String> = scopes.split(",").map { it.trim() }

    fun hasGrantType(grantType: String): Boolean = getGrantTypeList().contains(grantType)

    fun hasScope(scope: String): Boolean = getScopeList().contains(scope)

    fun isValidRedirectUri(uri: String): Boolean {
        val uriList = getRedirectUriList()
        if (uriList.isEmpty()) return false
        return uriList.any { pattern ->
            if (pattern.endsWith("*")) {
                uri.startsWith(pattern.dropLast(1))
            } else {
                uri == pattern
            }
        }
    }
}