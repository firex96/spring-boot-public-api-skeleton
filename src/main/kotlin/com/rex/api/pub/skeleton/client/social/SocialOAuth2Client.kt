package com.rex.api.pub.skeleton.client.social

import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.enums.auth.SocialProvider

interface SocialOAuth2Client {
    val provider: SocialProvider

    fun getAuthorizationUrl(state: String): String

    fun getAccessToken(code: String): OAuth2TokenResponse

    fun getUserInfo(accessToken: String): SocialUserInfo
}