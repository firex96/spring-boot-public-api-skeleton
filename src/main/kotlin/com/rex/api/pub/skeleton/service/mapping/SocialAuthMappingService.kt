package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.mapper.SocialAuthMapper
import com.rex.api.pub.skeleton.model.auth.SocialAccountModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.auth.SocialAuthBizService
import org.springframework.stereotype.Service

/** 소셜 로그인 API 매핑 — `SocialAuthController`. */
@Service
class SocialAuthMappingService(
    private val socialAuthBizService: SocialAuthBizService,
    private val socialAuthMapper: SocialAuthMapper,
    private val authMapper: AuthMapper,
) {
    fun getAuthorizationUrl(provider: SocialProvider, state: String): String = socialAuthBizService.getAuthorizationUrl(provider, state)

    fun socialLogin(provider: SocialProvider, code: String, deviceType: String?, deviceInfo: String?, ipAddress: String?): TokenResponseModel =
        authMapper.toTokenResponseModel(
            socialAuthBizService.socialLogin(
                provider = provider,
                code = code,
                deviceType = deviceType,
                deviceInfo = deviceInfo,
                ipAddress = ipAddress,
            ),
        )

    fun linkSocialAccount(userId: Long, provider: SocialProvider, code: String, ipAddress: String?): SocialAccountModel =
        socialAuthMapper.toSocialAccountModel(
            socialAuthBizService.linkSocialAccount(userId = userId, provider = provider, code = code, ipAddress = ipAddress),
        )

    fun unlinkSocialAccount(userId: Long, provider: SocialProvider, ipAddress: String?) {
        socialAuthBizService.unlinkSocialAccount(userId, provider, ipAddress)
    }

    fun getSocialAccounts(userId: Long): List<SocialAccountModel> =
        socialAuthMapper.toSocialAccountModelList(socialAuthBizService.getSocialAccounts(userId))
}