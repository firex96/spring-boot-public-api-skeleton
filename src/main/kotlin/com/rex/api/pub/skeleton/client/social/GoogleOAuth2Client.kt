package com.rex.api.pub.skeleton.client.social

import com.rex.api.pub.skeleton.config.security.OAuth2Properties
import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.dto.auth.social.GoogleUserResponse
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.util.logger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class GoogleOAuth2Client(
    private val oAuth2Properties: OAuth2Properties,
    private val webClient: WebClient,
) : SocialOAuth2Client {
    private val log = logger()

    override val provider = SocialProvider.GOOGLE

    companion object {
        private const val AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
    }

    private val config: com.rex.api.pub.skeleton.config.security.OAuth2ProviderProperties
        get() = oAuth2Properties.google ?: throw AuthenticationException("구글 로그인이 설정되지 않았습니다")

    override fun getAuthorizationUrl(state: String): String {
        val redirectUri = "${oAuth2Properties.redirectBaseUrl}/auth/oauth2/google/callback"
        val scope =
            config.scope.ifEmpty {
                listOf("openid", "email", "profile")
            }.joinToString(" ")

        return buildString {
            append(AUTHORIZATION_URL)
            append("?response_type=code")
            append("&client_id=${config.clientId}")
            append("&redirect_uri=${URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)}")
            append("&state=$state")
            append("&scope=${URLEncoder.encode(scope, StandardCharsets.UTF_8)}")
            append("&access_type=offline")
            append("&prompt=consent")
        }
    }

    override fun getAccessToken(code: String): OAuth2TokenResponse {
        val redirectUri = "${oAuth2Properties.redirectBaseUrl}/auth/oauth2/google/callback"

        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", config.clientId)
                add("client_secret", config.clientSecret)
                add("code", code)
                add("redirect_uri", redirectUri)
            }

        val response =
            webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: throw AuthenticationException("구글 토큰 발급 실패")

        if (response["error"] != null) {
            log.error("Google token error: {}", response)
            throw AuthenticationException("구글 토큰 발급 실패: ${response["error_description"]}")
        }

        return OAuth2TokenResponse(
            accessToken = response["access_token"] as String,
            refreshToken = response["refresh_token"] as? String,
            tokenType = response["token_type"] as? String,
            expiresIn = (response["expires_in"] as? Number)?.toLong(),
        )
    }

    override fun getUserInfo(accessToken: String): SocialUserInfo {
        val response =
            webClient.get()
                .uri(USER_INFO_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .bodyToMono(GoogleUserResponse::class.java)
                .block() ?: throw AuthenticationException("구글 사용자 정보 조회 실패")

        return SocialUserInfo(
            provider = SocialProvider.GOOGLE,
            providerId = response.id,
            email = response.email,
            name = response.name,
            profileImage = response.picture,
        )
    }
}