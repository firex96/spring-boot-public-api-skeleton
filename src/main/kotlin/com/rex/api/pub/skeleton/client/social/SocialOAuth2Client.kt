package com.rex.api.pub.skeleton.client.social

import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.enums.auth.SocialProvider

interface SocialOAuth2Client {
    val provider: SocialProvider

    fun getAuthorizationUrl(state: String): String

    /** [state] 는 서버 콜백 경로에서 받은 값이다 — 토큰 교환에 state 를 요구하는 provider(Naver)만 쓴다. 네이티브 코드 교환 경로는 null. */
    fun getAccessToken(code: String, state: String?): OAuth2TokenResponse

    fun getUserInfo(accessToken: String): SocialUserInfo
}