package com.rex.api.pub.skeleton.fixture

import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationConsentDto
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientWithSecretDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2TokenResponseDto
import com.rex.api.pub.skeleton.dto.oauth2.TokenIntrospectionRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.TokenIntrospectionResponseDto
import com.rex.api.pub.skeleton.dto.oauth2.TokenRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.TokenRevocationRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.UpdateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.model.oauth2.AuthorizeDecisionModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.OAuth2ClientEntity
import java.time.LocalDateTime
import java.util.UUID

/**
 * OAuth2 클라이언트 관련 테스트 픽스처.
 *
 * OAuth2 Authorization Server API 테스트에서 사용되는 요청/응답 객체와 엔티티를 생성합니다.
 *
 * 사용 예시:
 * ```kotlin
 * val createRequest = OAuth2ClientFixture.createClientRequest()
 * val clientDto = OAuth2ClientFixture.clientDto()
 * ```
 */
object OAuth2ClientFixture {
    // 기본 클라이언트 정보
    const val DEFAULT_CLIENT_ID = "test-client-id"
    const val DEFAULT_CLIENT_SECRET = "test-client-secret-hash"
    const val DEFAULT_CLIENT_NAME = "테스트 클라이언트"
    const val DEFAULT_DESCRIPTION = "테스트용 OAuth2 클라이언트"
    const val DEFAULT_REDIRECT_URI = "http://localhost:3000/callback"

    val DEFAULT_GRANT_TYPES = listOf("authorization_code", "refresh_token")
    val DEFAULT_SCOPES = listOf("openid", "profile", "email")

    /**
     * OAuth2 클라이언트 생성 요청 DTO.
     */
    fun createClientRequest(
        clientName: String = DEFAULT_CLIENT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        grantTypes: List<String> = DEFAULT_GRANT_TYPES,
        redirectUris: List<String> = listOf(DEFAULT_REDIRECT_URI),
        scopes: List<String> = DEFAULT_SCOPES,
        accessTokenValidity: Int = 3600,
        refreshTokenValidity: Int = 604800,
        isFirstParty: Boolean = false,
        requirePkce: Boolean = false,
    ): CreateOAuth2ClientRequestDto =
        CreateOAuth2ClientRequestDto(
            clientName = clientName,
            description = description,
            grantTypes = grantTypes,
            redirectUris = redirectUris,
            scopes = scopes,
            accessTokenValidity = accessTokenValidity,
            refreshTokenValidity = refreshTokenValidity,
            isFirstParty = isFirstParty,
            requirePkce = requirePkce,
        )

    /**
     * OAuth2 클라이언트 수정 요청 DTO.
     */
    fun updateClientRequest(
        clientName: String = "수정된 클라이언트",
        description: String? = "수정된 설명",
        grantTypes: List<String>? = null,
        redirectUris: List<String>? = null,
        scopes: List<String>? = null,
        accessTokenValidity: Int? = null,
        refreshTokenValidity: Int? = null,
        isFirstParty: Boolean? = null,
        requirePkce: Boolean? = null,
        isActive: Boolean? = null,
    ): UpdateOAuth2ClientRequestDto =
        UpdateOAuth2ClientRequestDto(
            clientName = clientName,
            description = description,
            grantTypes = grantTypes,
            redirectUris = redirectUris,
            scopes = scopes,
            accessTokenValidity = accessTokenValidity,
            refreshTokenValidity = refreshTokenValidity,
            isFirstParty = isFirstParty,
            requirePkce = requirePkce,
            isActive = isActive,
        )

    /**
     * OAuth2 클라이언트 DTO.
     */
    fun clientDto(
        id: Long = 1L,
        clientId: String = DEFAULT_CLIENT_ID,
        clientName: String = DEFAULT_CLIENT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        grantTypes: List<String> = DEFAULT_GRANT_TYPES,
        redirectUris: List<String> = listOf(DEFAULT_REDIRECT_URI),
        scopes: List<String> = DEFAULT_SCOPES,
        accessTokenValidity: Int = 3600,
        refreshTokenValidity: Int = 604800,
        isActive: Boolean = true,
        isFirstParty: Boolean = false,
        requirePkce: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
    ): OAuth2ClientDto =
        OAuth2ClientDto(
            id = id,
            clientId = clientId,
            clientName = clientName,
            description = description,
            grantTypes = grantTypes,
            redirectUris = redirectUris,
            scopes = scopes,
            accessTokenValidity = accessTokenValidity,
            refreshTokenValidity = refreshTokenValidity,
            isActive = isActive,
            isFirstParty = isFirstParty,
            requirePkce = requirePkce,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * OAuth2 클라이언트 DTO (시크릿 포함).
     */
    fun clientWithSecretDto(
        id: Long = 1L,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = "plain-text-secret",
        clientName: String = DEFAULT_CLIENT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        grantTypes: List<String> = DEFAULT_GRANT_TYPES,
        redirectUris: List<String> = listOf(DEFAULT_REDIRECT_URI),
        scopes: List<String> = DEFAULT_SCOPES,
        accessTokenValidity: Int = 3600,
        refreshTokenValidity: Int = 604800,
        isActive: Boolean = true,
        isFirstParty: Boolean = false,
        requirePkce: Boolean = false,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
    ): OAuth2ClientWithSecretDto =
        OAuth2ClientWithSecretDto(
            id = id,
            clientId = clientId,
            clientSecret = clientSecret,
            clientName = clientName,
            description = description,
            grantTypes = grantTypes,
            redirectUris = redirectUris,
            scopes = scopes,
            accessTokenValidity = accessTokenValidity,
            refreshTokenValidity = refreshTokenValidity,
            isActive = isActive,
            isFirstParty = isFirstParty,
            requirePkce = requirePkce,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * OAuth2 클라이언트 엔티티 생성 (DB 저장용).
     */
    fun clientEntity(
        id: Long = 0L,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET,
        clientName: String = DEFAULT_CLIENT_NAME,
        description: String? = DEFAULT_DESCRIPTION,
        grantTypes: String = DEFAULT_GRANT_TYPES.joinToString(","),
        redirectUris: String? = DEFAULT_REDIRECT_URI,
        scopes: String = DEFAULT_SCOPES.joinToString(","),
        accessTokenValidity: Int = 3600,
        refreshTokenValidity: Int = 604800,
        isActive: Boolean = true,
        isFirstParty: Boolean = false,
        requirePkce: Boolean = false,
    ): OAuth2ClientEntity =
        OAuth2ClientEntity(
            id = id,
            clientId = clientId,
            clientSecret = clientSecret,
            clientName = clientName,
            description = description,
            grantTypes = grantTypes,
            redirectUris = redirectUris,
            scopes = scopes,
            accessTokenValidity = accessTokenValidity,
            refreshTokenValidity = refreshTokenValidity,
            isActive = isActive,
            isFirstParty = isFirstParty,
            requirePkce = requirePkce,
        )

    // ===== Authorization Endpoint Fixtures =====

    /**
     * Authorization 요청 DTO.
     */
    fun authorizationRequest(
        responseType: String = "code",
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        scope: String? = DEFAULT_SCOPES.joinToString(" "),
        state: String? = UUID.randomUUID().toString(),
        codeChallenge: String? = null,
        codeChallengeMethod: String? = null,
        nonce: String? = null,
    ): AuthorizationRequestDto =
        AuthorizationRequestDto(
            responseType = responseType,
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            nonce = nonce,
        )

    /**
     * 사용자 동의 화면 DTO.
     */
    fun authorizationConsent(
        clientId: String = DEFAULT_CLIENT_ID,
        clientName: String = DEFAULT_CLIENT_NAME,
        requestedScopes: List<String> = DEFAULT_SCOPES,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        state: String? = UUID.randomUUID().toString(),
    ): AuthorizationConsentDto =
        AuthorizationConsentDto(
            clientId = clientId,
            clientName = clientName,
            requestedScopes = requestedScopes,
            redirectUri = redirectUri,
            state = state,
        )

    /** 인가·동의 판정 Model — 컨트롤러 슬라이스 테스트가 MappingService 목의 반환값으로 쓴다. */
    fun authorizeDecision(
        nextStep: AuthorizeNextStep,
        code: String? = null,
        error: String? = null,
        errorDescription: String? = null,
    ): AuthorizeDecisionModel = AuthorizeDecisionModel(nextStep = nextStep, code = code, error = error, errorDescription = errorDescription)

    // ===== Token Endpoint Fixtures =====

    /**
     * Authorization Code 토큰 요청 DTO.
     */
    fun tokenRequestWithAuthorizationCode(
        code: String = "test-authorization-code",
        redirectUri: String = DEFAULT_REDIRECT_URI,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = "plain-text-secret",
        codeVerifier: String? = null,
    ): TokenRequestDto =
        TokenRequestDto(
            grantType = "authorization_code",
            code = code,
            redirectUri = redirectUri,
            clientId = clientId,
            clientSecret = clientSecret,
            codeVerifier = codeVerifier,
        )

    /**
     * Refresh Token 토큰 요청 DTO.
     */
    fun tokenRequestWithRefreshToken(
        refreshToken: String = "test-refresh-token",
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = "plain-text-secret",
    ): TokenRequestDto =
        TokenRequestDto(
            grantType = "refresh_token",
            refreshToken = refreshToken,
            clientId = clientId,
            clientSecret = clientSecret,
        )

    /**
     * Client Credentials 토큰 요청 DTO.
     */
    fun tokenRequestWithClientCredentials(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = "plain-text-secret",
        scope: String? = DEFAULT_SCOPES.joinToString(" "),
    ): TokenRequestDto =
        TokenRequestDto(
            grantType = "client_credentials",
            clientId = clientId,
            clientSecret = clientSecret,
            scope = scope,
        )

    /**
     * OAuth2 토큰 응답 DTO.
     */
    fun tokenResponse(
        accessToken: String = "test-access-token",
        tokenType: String = "Bearer",
        expiresIn: Long = 3600,
        refreshToken: String? = "test-refresh-token",
        scope: String? = DEFAULT_SCOPES.joinToString(" "),
    ): OAuth2TokenResponseDto =
        OAuth2TokenResponseDto(
            accessToken = accessToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            refreshToken = refreshToken,
            scope = scope,
        )

    // ===== Token Introspection Fixtures =====

    /**
     * 토큰 검증 요청 DTO.
     */
    fun introspectionRequest(
        token: String = "test-access-token",
        tokenTypeHint: String? = "access_token",
    ): TokenIntrospectionRequestDto =
        TokenIntrospectionRequestDto(
            token = token,
            tokenTypeHint = tokenTypeHint,
        )

    /**
     * 유효한 토큰 검증 응답 DTO.
     */
    fun introspectionResponseActive(
        scope: String? = DEFAULT_SCOPES.joinToString(" "),
        clientId: String = DEFAULT_CLIENT_ID,
        username: String? = "testuser",
        tokenType: String = "Bearer",
        exp: Long = System.currentTimeMillis() / 1000 + 3600,
        iat: Long = System.currentTimeMillis() / 1000,
        sub: String = "1",
        aud: String = "skeleton-apps",
        iss: String = "public-api-skeleton",
        jti: String = UUID.randomUUID().toString(),
    ): TokenIntrospectionResponseDto =
        TokenIntrospectionResponseDto(
            active = true,
            scope = scope,
            clientId = clientId,
            username = username,
            tokenType = tokenType,
            exp = exp,
            iat = iat,
            sub = sub,
            aud = aud,
            iss = iss,
            jti = jti,
        )

    /**
     * 만료된/무효한 토큰 검증 응답 DTO.
     */
    fun introspectionResponseInactive(): TokenIntrospectionResponseDto =
        TokenIntrospectionResponseDto(
            active = false,
        )

    // ===== Token Revocation Fixtures =====

    /**
     * 토큰 취소 요청 DTO.
     */
    fun revocationRequest(
        token: String = "test-access-token",
        tokenTypeHint: String? = "access_token",
    ): TokenRevocationRequestDto =
        TokenRevocationRequestDto(
            token = token,
            tokenTypeHint = tokenTypeHint,
        )

    /**
     * 잘못된 형식의 클라이언트 요청 모음 (Validation 테스트용).
     */
    object Invalid {
        /** 빈 클라이언트 이름 */
        fun createClientRequestWithEmptyName() =
            CreateOAuth2ClientRequestDto(
                clientName = "",
            )

        /** 너무 긴 클라이언트 이름 (100자 초과) */
        fun createClientRequestWithTooLongName() =
            CreateOAuth2ClientRequestDto(
                clientName = "a".repeat(101),
            )

        /** 빈 grant_type 토큰 요청 */
        fun tokenRequestWithEmptyGrantType() =
            TokenRequestDto(
                grantType = "",
            )

        /** 빈 토큰 introspection 요청 */
        fun introspectionRequestWithEmptyToken() =
            TokenIntrospectionRequestDto(
                token = "",
            )

        /** 빈 토큰 revocation 요청 */
        fun revocationRequestWithEmptyToken() =
            TokenRevocationRequestDto(
                token = "",
            )
    }
}