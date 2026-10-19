package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.LoginOutcomeDto
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.mapper.MfaMapper
import com.rex.api.pub.skeleton.mapper.SocialAuthMapper
import com.rex.api.pub.skeleton.model.auth.BrowserLoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.LoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.SocialAccountModel
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.auth.SocialAuthBizService
import com.rex.api.pub.skeleton.service.auth.SocialAuthorizationStateBizService
import com.rex.api.pub.skeleton.service.auth.TokenHandoffBizService
import org.springframework.stereotype.Service

/** 소셜 로그인 API 매핑 — `SocialAuthController`. */
@Service
class SocialAuthMappingService(
    private val socialAuthBizService: SocialAuthBizService,
    private val socialAuthMapper: SocialAuthMapper,
    private val authMapper: AuthMapper,
    private val socialAuthorizationStateBizService: SocialAuthorizationStateBizService,
    private val authBizService: AuthBizService,
    private val tokenHandoffBizService: TokenHandoffBizService,
    private val mfaMapper: MfaMapper,
) {
    /** 브라우저 세션에 묶인 state 를 발급하고 provider 인가 URL 을 돌려준다. */
    fun issueAuthorizationRedirect(provider: SocialProvider, browserSessionId: String): String =
        socialAuthBizService.getAuthorizationUrl(provider, socialAuthorizationStateBizService.issue(browserSessionId, provider))

    /** 콜백의 state 를 한 번만 소비한다 — 같은 브라우저 세션·같은 provider 에서 발급한 값일 때만 true. */
    fun consumeState(provider: SocialProvider, browserSessionId: String, state: String?): Boolean =
        socialAuthorizationStateBizService.consume(browserSessionId, provider, state)

    /** 네이티브 코드 교환 — 서버가 발급한 state 가 없는 흐름이라 provider 에도 state 를 넘기지 않는다. */
    fun socialLogin(
        provider: SocialProvider,
        code: String,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): LoginOutcomeModel =
        when (
            val outcome =
                socialAuthBizService.socialLogin(
                    provider = provider,
                    code = code,
                    state = null,
                    deviceType = deviceType,
                    deviceInfo = deviceInfo,
                    ipAddress = ipAddress,
                    origin = MfaOrigin.API_SOCIAL,
                )
        ) {
            is LoginOutcomeDto.Tokens -> LoginOutcomeModel.Tokens(authMapper.toTokenResponseModel(outcome.session.tokens))
            is LoginOutcomeDto.MfaRequired -> LoginOutcomeModel.MfaRequired(mfaMapper.toMfaChallengeModel(outcome.challenge))
        }

    /**
     * 브라우저 콜백의 소셜 로그인 — 토큰은 반환하지 않고 교환 대기(`TokenHandoffBizService`)에 맡긴다.
     * 돌려주는 principal 은 컨트롤러가 브라우저 세션에 로그인시킨다. 페이지는 `POST /auth/session/token` 으로 토큰을 받는다.
     * 2차 인증 대상이면 아무것도 맡기지 않고 challenge 토큰만 돌려준다 — 토큰은 2단계를 통과한 뒤 맡긴다(`BrowserMfaMappingService`).
     */
    fun socialLoginForBrowser(provider: SocialProvider, code: String, state: String?, deviceInfo: String?, ipAddress: String?): BrowserLoginOutcomeModel {
        val outcome =
            socialAuthBizService.socialLogin(
                provider = provider,
                code = code,
                state = state,
                deviceType = "WEB",
                deviceInfo = deviceInfo,
                ipAddress = ipAddress,
                origin = MfaOrigin.SOCIAL_BROWSER,
            )
        return when (outcome) {
            is LoginOutcomeDto.MfaRequired -> BrowserLoginOutcomeModel.MfaRequired(outcome.challenge.token)
            is LoginOutcomeDto.Tokens -> {
                val principal = authBizService.browserPrincipalOf(outcome.session)
                tokenHandoffBizService.put(principal.sessionId, outcome.session.tokens)
                BrowserLoginOutcomeModel.SignedIn(authMapper.toBrowserPrincipalModel(principal))
            }
        }
    }

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