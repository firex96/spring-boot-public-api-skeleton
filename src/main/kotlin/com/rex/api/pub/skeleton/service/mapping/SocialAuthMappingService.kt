package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.mapper.SocialAuthMapper
import com.rex.api.pub.skeleton.model.auth.SocialAccountModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.auth.SocialAuthBizService
import com.rex.api.pub.skeleton.service.auth.SocialAuthorizationStateBizService
import org.springframework.stereotype.Service

/** 소셜 로그인 API 매핑 — `SocialAuthController`. */
@Service
class SocialAuthMappingService(
    private val socialAuthBizService: SocialAuthBizService,
    private val socialAuthMapper: SocialAuthMapper,
    private val authMapper: AuthMapper,
    private val socialAuthorizationStateBizService: SocialAuthorizationStateBizService,
) {
    /** 브라우저 세션에 묶인 state 를 발급하고 provider 인가 URL 을 돌려준다. */
    fun issueAuthorizationRedirect(provider: SocialProvider, browserSessionId: String): String =
        socialAuthBizService.getAuthorizationUrl(provider, socialAuthorizationStateBizService.issue(browserSessionId, provider))

    /** 콜백의 state 를 한 번만 소비한다 — 같은 브라우저 세션·같은 provider 에서 발급한 값일 때만 true. */
    fun consumeState(provider: SocialProvider, browserSessionId: String, state: String?): Boolean =
        socialAuthorizationStateBizService.consume(browserSessionId, provider, state)

    fun socialLogin(
        provider: SocialProvider,
        code: String,
        state: String?,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): TokenResponseModel =
        authMapper.toTokenResponseModel(
            socialAuthBizService.socialLogin(
                provider = provider,
                code = code,
                state = state,
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