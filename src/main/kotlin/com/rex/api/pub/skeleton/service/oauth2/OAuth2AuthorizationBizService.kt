package com.rex.api.pub.skeleton.service.oauth2

import com.rex.api.pub.skeleton.config.security.JwtProperties
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationConsentDto
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizeDecisionDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2AccessTokenDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2ClientDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2RefreshTokenDto
import com.rex.api.pub.skeleton.dto.oauth2.OAuth2TokenResponseDto
import com.rex.api.pub.skeleton.dto.oauth2.TokenIntrospectionResponseDto
import com.rex.api.pub.skeleton.dto.oauth2.UserInfoGrantDto
import com.rex.api.pub.skeleton.dto.oidc.OidcTokenResponseDto
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.oauth2.OAuth2TokenEntityService
import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.oidc.IdTokenBizService
import com.rex.api.pub.skeleton.util.logger
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * OAuth2 인가 서버 비즈니스 — 인가 코드·토큰 발급·검사·폐기와 클라이언트 자격 인증.
 * 인가 요청의 판정(`authorize`·`consent`)은 여기서 내리고 컨트롤러는 리다이렉트 URL 만 조립한다.
 */
@Service
class OAuth2AuthorizationBizService(
    private val oAuth2ClientBizService: OAuth2ClientBizService,
    private val oAuth2TokenEntityService: OAuth2TokenEntityService,
    private val userEntityService: UserEntityService,
    private val jwtProperties: JwtProperties,
    private val idTokenBizService: IdTokenBizService,
    private val sessionCacheBizService: SessionCacheBizService,
) {
    private val log = logger()

    companion object {
        private const val AUTHORIZATION_CODE_LENGTH = 32
        private const val AUTHORIZATION_CODE_VALIDITY_SECONDS = 600L // 10분

        const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
        const val GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"

        // ===== 지원값 — discovery 광고(`OidcMappingService`)와 검사 로직이 함께 쓰는 유일한 정본 =====
        // 여기 없는 값을 광고하지 않고, 광고하지 않는 값을 받지 않는다. 지원을 늘리면 여기와 처리 로직을 같이 바꾼다.
        val SUPPORTED_RESPONSE_TYPES: List<String> = listOf("code")
        val SUPPORTED_GRANT_TYPES: List<String> = listOf(GRANT_TYPE_AUTHORIZATION_CODE, GRANT_TYPE_REFRESH_TOKEN, GRANT_TYPE_CLIENT_CREDENTIALS)
        val SUPPORTED_CODE_CHALLENGE_METHODS: List<String> = listOf("S256", "plain")
        val SUPPORTED_TOKEN_ENDPOINT_AUTH_METHODS: List<String> = listOf("client_secret_basic", "client_secret_post") // ClientCredentialsResolver
        val SUPPORTED_SCOPES: List<String> = listOf("openid", "profile", "email", "offline_access")

        const val ERROR_INVALID_REQUEST = "invalid_request"
        const val ERROR_UNAUTHORIZED_CLIENT = "unauthorized_client"
        const val ERROR_ACCESS_DENIED = "access_denied"
        const val ERROR_LOGIN_REQUIRED = "login_required" // OIDC Core §3.1.2.6 — 사용자 인증이 필요하다
        const val ERROR_UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type"
        const val ERROR_INVALID_SCOPE = "invalid_scope"
        const val ERROR_INVALID_GRANT = "invalid_grant"
        const val ERROR_UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"
        const val ERROR_INVALID_CLIENT = "invalid_client"
        const val ERROR_INVALID_TOKEN = "invalid_token" // RFC 6750 §3.1
        const val SCOPE_OPENID = "openid"
        const val ERROR_INSUFFICIENT_SCOPE = "insufficient_scope" // RFC 6750 §3.1
    }

    private val signingKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))
    }

    // ===== Authorization Endpoint =====

    /**
     * `GET /oauth2/authorize` 의 판정. client·redirect_uri 검증 실패는 예외로 던진다(RFC 6749 §4.1.2.1 — 리다이렉트하지 않는다).
     * 그 뒤의 오류(invalid_scope 등)는 검증된 redirect_uri 로 알릴 수 있으므로 `ERROR` 판정으로 돌려준다.
     */
    @Transactional(transactionManager = "authTransactionManager") // 인가 코드 저장이 auth 트랜잭션에 참여해야 한다 — primary 는 default TM
    fun authorize(request: AuthorizationRequestDto, userId: Long?, sessionId: String?): AuthorizeDecisionDto {
        val client = validateClientAndRedirectUri(request)
        return try {
            validateAuthorizationParameters(request, client)
            // 인증 시각은 브라우저 세션이 가리키는 사용자 세션에서 읽는다 — 세션이 사라졌으면 로그인부터 다시
            val authTime = sessionId?.let { authenticatedAtOf(it) }
            when {
                userId == null || authTime == null -> decision(AuthorizeNextStep.LOGIN_REQUIRED)
                client.isFirstParty -> decision(AuthorizeNextStep.CODE_ISSUED, code = issueAuthorizationCode(request, userId, authTime))
                else -> decision(AuthorizeNextStep.CONSENT_REQUIRED)
            }
        } catch (e: OAuth2Exception) {
            decision(AuthorizeNextStep.ERROR, error = e.error, errorDescription = e.errorDescription)
        }
    }

    /** `POST /oauth2/consent` 의 판정 — 거부는 `access_denied` 오류 판정, 승인은 코드 발급. */
    @Transactional(transactionManager = "authTransactionManager") // 인가 코드 저장이 auth 트랜잭션에 참여해야 한다 — primary 는 default TM
    fun consent(request: AuthorizationRequestDto, userId: Long, sessionId: String, approve: Boolean): AuthorizeDecisionDto {
        validateAuthorizationRequest(request)
        if (!approve) {
            return decision(AuthorizeNextStep.ERROR, error = ERROR_ACCESS_DENIED, errorDescription = "사용자가 동의를 거부했습니다")
        }
        val authTime =
            authenticatedAtOf(sessionId)
                ?: return decision(AuthorizeNextStep.ERROR, error = ERROR_LOGIN_REQUIRED, errorDescription = "로그인 세션이 만료되었습니다")
        return decision(AuthorizeNextStep.CODE_ISSUED, code = issueAuthorizationCode(request, userId, authTime))
    }

    /**
     * client·redirect_uri 검증 — RFC 6749 §4.1.2.1: 이 검증이 실패하면 **리다이렉트하지 않는다**.
     * 컨트롤러는 이 메서드가 돌려준 뒤에만 `request.redirectUri` 를 신뢰한다.
     */
    fun validateClientAndRedirectUri(request: AuthorizationRequestDto): OAuth2ClientDto {
        val client = oAuth2ClientBizService.getActiveClient(request.clientId)
            ?: throw OAuth2Exception(ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")
        if (!client.hasGrantType(GRANT_TYPE_AUTHORIZATION_CODE)) {
            throw OAuth2Exception(ERROR_UNAUTHORIZED_CLIENT, "클라이언트가 authorization_code flow를 지원하지 않습니다")
        }
        if (!client.isValidRedirectUri(request.redirectUri)) {
            throw OAuth2Exception(ERROR_INVALID_REQUEST, "유효하지 않은 redirect_uri입니다")
        }
        return client
    }

    /** 검증된 client 에 대한 나머지 파라미터 검증 — 실패는 검증된 redirect_uri 로 알릴 수 있다. */
    fun validateAuthorizationParameters(request: AuthorizationRequestDto, client: OAuth2ClientDto) {
        if (request.responseType !in SUPPORTED_RESPONSE_TYPES) {
            throw OAuth2Exception(ERROR_UNSUPPORTED_RESPONSE_TYPE, "지원하지 않는 response_type입니다")
        }
        if (client.requirePkce && request.codeChallenge.isNullOrBlank()) {
            throw OAuth2Exception(ERROR_INVALID_REQUEST, "PKCE code_challenge가 필요합니다")
        }
        if (!request.codeChallengeMethod.isNullOrBlank() &&
            request.codeChallengeMethod !in SUPPORTED_CODE_CHALLENGE_METHODS
        ) {
            throw OAuth2Exception(ERROR_INVALID_REQUEST, "지원하지 않는 code_challenge_method입니다")
        }
        request.scope?.split(" ")?.forEach { scope ->
            if (!client.hasScope(scope)) {
                throw OAuth2Exception(ERROR_INVALID_SCOPE, "지원하지 않는 scope입니다: $scope")
            }
        }
    }

    /** client·redirect_uri 검증 뒤 나머지 파라미터 검증 — 둘의 합성. */
    fun validateAuthorizationRequest(request: AuthorizationRequestDto): OAuth2ClientDto =
        validateClientAndRedirectUri(request).also { validateAuthorizationParameters(request, it) }

    fun getConsentInfo(request: AuthorizationRequestDto, client: OAuth2ClientDto): AuthorizationConsentDto {
        val requestedScopes = request.scope?.split(" ") ?: client.scopes
        return AuthorizationConsentDto(
            clientId = client.clientId,
            clientName = client.clientName,
            requestedScopes = requestedScopes,
            redirectUri = request.redirectUri,
            state = request.state,
        )
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리 읽기·쓰기가 한 트랜잭션(커넥션 1개)에 참여해야 한다 — primary 는 default TM
    fun createAuthorizationCode(
        clientId: String,
        userId: Long,
        redirectUri: String,
        scope: String?,
        codeChallenge: String?,
        codeChallengeMethod: String?,
        state: String?,
        nonce: String?,
        authTime: LocalDateTime,
    ): String {
        val code = generateAuthorizationCode()
        oAuth2TokenEntityService.createAuthorizationCode(
            code = code,
            clientId = clientId,
            userId = userId,
            redirectUri = redirectUri,
            scopes = scope?.replace(" ", ","),
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod ?: "plain",
            state = state,
            nonce = nonce,
            expiresAt = LocalDateTime.now().plusSeconds(AUTHORIZATION_CODE_VALIDITY_SECONDS),
            authTime = authTime,
        )
        return code
    }

    // ===== Token Endpoint =====

    /**
     * 클라이언트 자격 인증 — 시크릿이 **반드시** 있어야 한다. 모든 클라이언트는 시크릿을 갖고(`client_secret NOT NULL`)
     * public 클라이언트 유형이 없으므로, 시크릿 없이 `client_id` 만으로 통과시키면 그것이 곧 클라이언트 인증 우회다.
     * 성공하면 clientId.
     */
    fun authenticateClient(clientId: String?, clientSecret: String?): String {
        if (clientId.isNullOrBlank()) {
            throw OAuth2Exception(ERROR_INVALID_CLIENT, "client_id가 필요합니다")
        }
        if (clientSecret.isNullOrBlank()) {
            throw OAuth2Exception(ERROR_INVALID_CLIENT, "클라이언트 인증이 필요합니다")
        }
        if (!oAuth2ClientBizService.validateClientCredentials(clientId, clientSecret)) {
            throw OAuth2Exception(ERROR_INVALID_CLIENT, "클라이언트 인증에 실패했습니다")
        }
        return clientId
    }

    /** `POST /oauth2/token` — grant_type 분기. 인증된 clientId 를 받는다(`authenticateClient` 뒤). */
    @Transactional(transactionManager = "authTransactionManager") // 같은 클래스의 grant 메서드를 직접 부르므로 트랜잭션은 여기서 연다(자기 호출은 프록시를 지나지 않는다)
    fun issueTokenByGrant(
        grantType: String,
        code: String?,
        redirectUri: String?,
        clientId: String,
        clientSecret: String?,
        refreshToken: String?,
        scope: String?,
        codeVerifier: String?,
    ): OidcTokenResponseDto = when (grantType) {
        GRANT_TYPE_AUTHORIZATION_CODE -> {
            requireNotNull(code) { "code는 필수입니다" }
            requireNotNull(redirectUri) { "redirect_uri는 필수입니다" }
            exchangeAuthorizationCode(code = code, redirectUri = redirectUri, clientId = clientId, codeVerifier = codeVerifier)
        }
        GRANT_TYPE_REFRESH_TOKEN -> {
            requireNotNull(refreshToken) { "refresh_token은 필수입니다" }
            refreshAccessToken(refreshTokenValue = refreshToken, clientId = clientId, clientSecret = clientSecret, scope = scope).toOidc()
        }
        GRANT_TYPE_CLIENT_CREDENTIALS -> clientCredentialsGrant(clientId = clientId, scope = scope).toOidc()
        else -> throw OAuth2Exception(ERROR_UNSUPPORTED_GRANT_TYPE, "지원하지 않는 grant_type입니다: $grantType")
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리 읽기·쓰기가 한 트랜잭션(커넥션 1개)에 참여해야 한다 — primary 는 default TM
    fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String?,
    ): OidcTokenResponseDto {
        val authCode = oAuth2TokenEntityService.getUnusedAuthorizationCode(code)
            ?: throw OAuth2Exception(ERROR_INVALID_GRANT, "유효하지 않은 authorization code입니다")
        if (authCode.isUsed || authCode.expiresAt.isBefore(LocalDateTime.now())) {
            throw OAuth2Exception(ERROR_INVALID_GRANT, "만료되었거나 이미 사용된 authorization code입니다")
        }
        if (authCode.clientId != clientId) {
            throw OAuth2Exception(ERROR_INVALID_GRANT, "client_id가 일치하지 않습니다")
        }
        if (authCode.redirectUri != redirectUri) {
            throw OAuth2Exception(ERROR_INVALID_GRANT, "redirect_uri가 일치하지 않습니다")
        }

        // PKCE 검증
        val codeChallenge = authCode.codeChallenge
        if (!codeChallenge.isNullOrBlank()) {
            if (codeVerifier.isNullOrBlank()) {
                throw OAuth2Exception(ERROR_INVALID_GRANT, "code_verifier가 필요합니다")
            }
            if (!verifyCodeChallenge(codeVerifier, codeChallenge, authCode.codeChallengeMethod)) {
                throw OAuth2Exception(ERROR_INVALID_GRANT, "code_verifier가 유효하지 않습니다")
            }
        }

        oAuth2TokenEntityService.markAuthorizationCodeUsed(code, LocalDateTime.now())

        val client = oAuth2ClientBizService.getActiveClient(clientId)
            ?: throw OAuth2Exception(ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")

        return issueTokens(client = client, userId = authCode.userId, scopes = authCode.scopes, nonce = authCode.nonce, authTime = authCode.authTime)
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리 읽기·쓰기가 한 트랜잭션(커넥션 1개)에 참여해야 한다 — primary 는 default TM
    fun refreshAccessToken(
        refreshTokenValue: String,
        clientId: String,
        clientSecret: String?,
        scope: String?,
    ): OAuth2TokenResponseDto {
        val refreshTokenId = extractTokenId(refreshTokenValue)
            ?: throw OAuth2Exception(ERROR_INVALID_GRANT, "유효하지 않은 refresh token입니다")
        val refreshToken = oAuth2TokenEntityService.getUnrevokedRefreshToken(refreshTokenId)
            ?: throw OAuth2Exception(ERROR_INVALID_GRANT, "유효하지 않은 refresh token입니다")
        if (!refreshToken.isActive()) {
            throw OAuth2Exception(ERROR_INVALID_GRANT, "만료되었거나 취소된 refresh token입니다")
        }
        if (refreshToken.clientId != clientId) {
            throw OAuth2Exception(ERROR_INVALID_GRANT, "client_id가 일치하지 않습니다")
        }

        val client = oAuth2ClientBizService.getActiveClient(clientId)
            ?: throw OAuth2Exception(ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")

        // RFC 6749 §6 — 갱신 scope 는 원래 승인 범위를 넘을 수 없다(넘으면 userinfo 등에서 사용자가 승인하지 않은 정보가 나간다)
        val tokenScopes = scope?.let { requested -> narrowScopes(requested, granted = scopeSet(refreshToken.scopes)) } ?: refreshToken.scopes

        // 기존 access token 폐기 후 새로 발급(refresh token 은 유지)
        oAuth2TokenEntityService.revokeAccessTokensByRefreshTokenId(refreshTokenId, LocalDateTime.now())
        return issueAccessToken(client = client, userId = refreshToken.userId, scopes = tokenScopes, refreshTokenId = refreshTokenId)
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리 읽기·쓰기가 한 트랜잭션(커넥션 1개)에 참여해야 한다 — primary 는 default TM
    fun clientCredentialsGrant(
        clientId: String,
        scope: String?,
    ): OAuth2TokenResponseDto {
        val client = oAuth2ClientBizService.getActiveClient(clientId)
            ?: throw OAuth2Exception(ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")
        if (!client.hasGrantType(GRANT_TYPE_CLIENT_CREDENTIALS)) {
            throw OAuth2Exception(ERROR_UNAUTHORIZED_CLIENT, "클라이언트가 client_credentials flow를 지원하지 않습니다")
        }
        // 요청 scope 는 클라이언트 등록 범위 안이어야 한다(RFC 6749 §3.3 — 등록하지 않은 scope 의 토큰을 내주지 않는다)
        val requestedScopes = scope?.let { narrowScopes(it, granted = client.scopes.toSet()) } ?: client.scopes.joinToString(",")
        return issueAccessTokenOnly(client = client, userId = null, scopes = requestedScopes)
    }

    // ===== UserInfo =====

    /**
     * `GET /oauth2/userinfo` 의 Bearer — **이 인가 서버가 발급한 access 토큰**이어야 한다(OIDC Core §5.3).
     *
     * 서명·만료를 지나고, `oauth2_access_tokens` 에 활성으로 있고(폐기·refresh 토큰·1st-party 로그인 토큰은 여기 없다),
     * 사용자 주체가 있고(client_credentials 토큰은 없다), `openid` scope 를 받았어야 한다. scope 는 발급 기록이 정본이다.
     */
    fun resolveUserInfoGrant(accessToken: String): UserInfoGrantDto {
        val tokenId = extractTokenId(accessToken) ?: throw OAuth2Exception(ERROR_INVALID_TOKEN, "유효하지 않은 토큰입니다")
        val record = oAuth2TokenEntityService.getAccessToken(tokenId)
        if (record == null || !record.isActive()) {
            throw OAuth2Exception(ERROR_INVALID_TOKEN, "유효하지 않은 토큰입니다")
        }
        val userId = record.userId ?: throw OAuth2Exception(ERROR_INVALID_TOKEN, "사용자 주체가 없는 토큰입니다")
        val scopes = scopeSet(record.scopes)
        if (SCOPE_OPENID !in scopes) {
            throw OAuth2Exception(ERROR_INSUFFICIENT_SCOPE, "openid scope 가 필요합니다")
        }
        return UserInfoGrantDto(userId = userId, scopes = scopes)
    }

    // ===== Introspection · Revocation =====

    fun introspectToken(token: String, clientId: String): TokenIntrospectionResponseDto {
        val tokenId = extractTokenId(token) ?: return TokenIntrospectionResponseDto(active = false)

        val accessToken = oAuth2TokenEntityService.getAccessToken(tokenId)
        if (accessToken != null) {
            if (!accessToken.isActive()) {
                return TokenIntrospectionResponseDto(active = false)
            }
            return TokenIntrospectionResponseDto(
                active = true,
                scope = accessToken.scopes?.replace(",", " "),
                clientId = accessToken.clientId,
                username = accessToken.userId?.let { loginIdOf(it) },
                tokenType = accessToken.tokenType,
                exp = accessToken.expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                iat = accessToken.issuedAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sub = accessToken.userId?.toString(),
                aud = accessToken.clientId,
                iss = jwtProperties.issuer,
                jti = accessToken.tokenId,
            )
        }

        val refreshToken = oAuth2TokenEntityService.getRefreshToken(tokenId)
        if (refreshToken != null && refreshToken.isActive()) {
            return TokenIntrospectionResponseDto(
                active = true,
                scope = refreshToken.scopes?.replace(",", " "),
                clientId = refreshToken.clientId,
                username = refreshToken.userId?.let { loginIdOf(it) },
                tokenType = "refresh_token",
                exp = refreshToken.expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                iat = refreshToken.issuedAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                sub = refreshToken.userId?.toString(),
                iss = jwtProperties.issuer,
                jti = refreshToken.tokenId,
            )
        }

        return TokenIntrospectionResponseDto(active = false)
    }

    @Transactional(transactionManager = "authTransactionManager") // auth 리포지토리 읽기·쓰기가 한 트랜잭션(커넥션 1개)에 참여해야 한다 — primary 는 default TM
    fun revokeToken(token: String, clientId: String) {
        val tokenId = extractTokenId(token) ?: return
        val now = LocalDateTime.now()

        if (oAuth2TokenEntityService.revokeAccessToken(tokenId, now) > 0) {
            return
        }
        // refresh token 이면 그 토큰으로 발급된 access token 도 함께 폐기
        if (oAuth2TokenEntityService.revokeRefreshToken(tokenId, now) > 0) {
            oAuth2TokenEntityService.revokeAccessTokensByRefreshTokenId(tokenId, now)
        }
    }

    // ===== Private =====

    /** 사용자 세션의 인증 완료 시각 — 세션이 없거나 폐기됐으면 null. */
    private fun authenticatedAtOf(sessionId: String): LocalDateTime? =
        sessionCacheBizService.getSession(sessionId)?.takeUnless { it.revoked }?.authenticatedAt?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()) }

    private fun issueAuthorizationCode(request: AuthorizationRequestDto, userId: Long, authTime: LocalDateTime): String =
        createAuthorizationCode(
            clientId = request.clientId,
            userId = userId,
            redirectUri = request.redirectUri,
            scope = request.scope,
            codeChallenge = request.codeChallenge,
            codeChallengeMethod = request.codeChallengeMethod,
            state = request.state,
            nonce = request.nonce,
            authTime = authTime,
        )

    private fun decision(
        nextStep: AuthorizeNextStep,
        code: String? = null,
        error: String? = null,
        errorDescription: String? = null,
    ) = AuthorizeDecisionDto(nextStep = nextStep, code = code, error = error, errorDescription = errorDescription)

    private fun loginIdOf(userId: Long): String? = userEntityService.getUserById(userId)?.loginId

    private fun issueTokens(
        client: OAuth2ClientDto,
        userId: Long?,
        scopes: String?,
        nonce: String? = null,
        authTime: LocalDateTime? = null,
    ): OidcTokenResponseDto {
        val accessTokenId = UUID.randomUUID().toString()
        val refreshTokenId = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        val accessTokenExpiry = now.plusSeconds(client.accessTokenValidity.toLong())
        val refreshTokenExpiry = now.plusSeconds(client.refreshTokenValidity.toLong())

        val accessToken = generateJwt(tokenId = accessTokenId, clientId = client.clientId, userId = userId, scopes = scopes, expiresAt = accessTokenExpiry)
        val refreshToken =
            generateJwt(
                tokenId = refreshTokenId,
                clientId = client.clientId,
                userId = userId,
                scopes = scopes,
                expiresAt = refreshTokenExpiry,
                isRefreshToken = true,
            )

        oAuth2TokenEntityService.createAccessToken(
            tokenId = accessTokenId,
            clientId = client.clientId,
            userId = userId,
            scopes = scopes,
            expiresAt = accessTokenExpiry,
            refreshTokenId = refreshTokenId,
        )
        oAuth2TokenEntityService.createRefreshToken(
            tokenId = refreshTokenId,
            clientId = client.clientId,
            userId = userId,
            scopes = scopes,
            expiresAt = refreshTokenExpiry,
        )

        // OIDC: openid scope 가 있으면 ID 토큰도 발급
        var idToken: String? = null
        val scopeSet = scopes?.split(",")?.toSet() ?: emptySet()
        if (scopeSet.contains("openid") && userId != null) {
            userEntityService.getUserById(userId)?.let { user ->
                idToken =
                    idTokenBizService.generateIdToken(
                        user = user,
                        clientId = client.clientId,
                        nonce = nonce,
                        scopes = scopeSet,
                        accessTokenHash = idTokenBizService.computeAtHash(accessToken),
                        // 배포 전에 만든 코드(auth_time NULL)는 발급 시각으로 대체한다
                        authTime = (authTime ?: now).atZone(ZoneId.systemDefault()).toInstant(),
                    )
            }
        }

        return OidcTokenResponseDto(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = client.accessTokenValidity.toLong(),
            refreshToken = refreshToken,
            scope = scopes?.replace(",", " "),
            idToken = idToken,
        )
    }

    private fun issueAccessToken(
        client: OAuth2ClientDto,
        userId: Long?,
        scopes: String?,
        refreshTokenId: String,
    ): OAuth2TokenResponseDto {
        val accessTokenId = UUID.randomUUID().toString()
        val accessTokenExpiry = LocalDateTime.now().plusSeconds(client.accessTokenValidity.toLong())
        val accessToken = generateJwt(tokenId = accessTokenId, clientId = client.clientId, userId = userId, scopes = scopes, expiresAt = accessTokenExpiry)
        oAuth2TokenEntityService.createAccessToken(
            tokenId = accessTokenId,
            clientId = client.clientId,
            userId = userId,
            scopes = scopes,
            expiresAt = accessTokenExpiry,
            refreshTokenId = refreshTokenId,
        )
        return OAuth2TokenResponseDto(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = client.accessTokenValidity.toLong(),
            scope = scopes?.replace(",", " "),
        )
    }

    private fun issueAccessTokenOnly(
        client: OAuth2ClientDto,
        userId: Long?,
        scopes: String?,
    ): OAuth2TokenResponseDto {
        val accessTokenId = UUID.randomUUID().toString()
        val accessTokenExpiry = LocalDateTime.now().plusSeconds(client.accessTokenValidity.toLong())
        val accessToken = generateJwt(tokenId = accessTokenId, clientId = client.clientId, userId = userId, scopes = scopes, expiresAt = accessTokenExpiry)
        oAuth2TokenEntityService.createAccessToken(
            tokenId = accessTokenId,
            clientId = client.clientId,
            userId = userId,
            scopes = scopes,
            expiresAt = accessTokenExpiry,
            refreshTokenId = null,
        )
        return OAuth2TokenResponseDto(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = client.accessTokenValidity.toLong(),
            scope = scopes?.replace(",", " "),
        )
    }

    private fun OAuth2TokenResponseDto.toOidc() = OidcTokenResponseDto(
        accessToken = accessToken,
        tokenType = tokenType,
        expiresIn = expiresIn,
        refreshToken = refreshToken,
        scope = scope,
    )

    // Entity 의 판정 메서드를 DTO 확장으로 — 본문은 Entity 의 것과 같다
    private fun scopeSet(scopes: String?): Set<String> = scopes.orEmpty().split(',', ' ').filter { it.isNotBlank() }.toSet()

    /** 요청 scope 가 [granted] 의 부분집합이면 저장 형식(쉼표)으로, 아니면 `invalid_scope`. */
    private fun narrowScopes(requested: String, granted: Set<String>): String {
        val requestedSet = scopeSet(requested)
        val exceeding = requestedSet - granted
        if (exceeding.isNotEmpty()) {
            throw OAuth2Exception(ERROR_INVALID_SCOPE, "승인·등록 범위를 넘는 scope 입니다: ${exceeding.joinToString(" ")}")
        }
        return requestedSet.joinToString(",")
    }

    private fun OAuth2ClientDto.hasGrantType(grantType: String): Boolean = grantTypes.contains(grantType)

    private fun OAuth2ClientDto.hasScope(scope: String): Boolean = scopes.contains(scope)

    private fun OAuth2ClientDto.isValidRedirectUri(uri: String): Boolean {
        if (redirectUris.isEmpty()) return false
        return redirectUris.any { pattern ->
            if (pattern.endsWith("*")) uri.startsWith(pattern.dropLast(1)) else uri == pattern
        }
    }

    private fun OAuth2AccessTokenDto.isActive(): Boolean = !isRevoked && !expiresAt.isBefore(LocalDateTime.now())

    private fun OAuth2RefreshTokenDto.isActive(): Boolean = !isRevoked && !expiresAt.isBefore(LocalDateTime.now())

    private fun generateJwt(
        tokenId: String,
        clientId: String,
        userId: Long?,
        scopes: String?,
        expiresAt: LocalDateTime,
        isRefreshToken: Boolean = false,
    ): String {
        val now = Instant.now()
        val expiry = expiresAt.atZone(ZoneId.systemDefault()).toInstant()

        val builder = Jwts.builder()
            .id(tokenId)
            .issuer(jwtProperties.issuer)
            .audience().add(clientId).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))

        if (userId != null) {
            builder.subject(userId.toString())
        }
        if (scopes != null) {
            builder.claim("scope", scopes.replace(",", " "))
        }
        if (isRefreshToken) {
            builder.claim("token_type", "refresh_token")
        }
        return builder.signWith(signingKey).compact()
    }

    private fun extractTokenId(token: String): String? = try {
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .id
    } catch (e: Exception) {
        log.debug("Token parsing failed: ${e.message}")
        null
    }

    private fun generateAuthorizationCode(): String {
        val bytes = ByteArray(AUTHORIZATION_CODE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).take(AUTHORIZATION_CODE_LENGTH)
    }

    private fun verifyCodeChallenge(
        codeVerifier: String,
        codeChallenge: String,
        method: String?,
    ): Boolean = when (method) {
        "S256" -> {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
            val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
            computed == codeChallenge
        }
        else -> codeVerifier == codeChallenge
    }
}