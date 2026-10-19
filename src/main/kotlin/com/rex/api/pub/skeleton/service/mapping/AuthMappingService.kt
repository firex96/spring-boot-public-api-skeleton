package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginOutcomeDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.MfaLoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.enums.auth.MfaOrigin
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.mapper.MfaMapper
import com.rex.api.pub.skeleton.model.auth.AccessTokenResponseModel
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.model.auth.BrowserLoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.LoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.auth.TokenHandoffBizService
import com.rex.api.pub.skeleton.service.mfa.MfaChallengeBizService
import org.springframework.stereotype.Service

/** 인증 API 매핑 — `AuthController`·`UserController` 의 DTO → BizService → Model. */
@Service
class AuthMappingService(
    private val authBizService: AuthBizService,
    private val tokenHandoffBizService: TokenHandoffBizService,
    private val mfaChallengeBizService: MfaChallengeBizService,
    private val mapper: AuthMapper,
    private val mfaMapper: MfaMapper,
) {
    /** 소셜 콜백이 맡겨 둔 토큰을 한 번만 꺼낸다 — 없거나 이미 꺼냈으면 null. */
    fun takeTokenHandoff(userSessionId: String): TokenResponseModel? = tokenHandoffBizService.take(userSessionId)?.let { mapper.toTokenResponseModel(it) }

    fun signup(request: SignupRequestDto): AuthUserInfoModel = mapper.toAuthUserInfoModel(authBizService.signup(request))

    /** 토큰(200) 또는 2차 인증 challenge(202). */
    fun login(request: LoginRequestDto, ipAddress: String?): LoginOutcomeModel =
        when (val outcome = authBizService.login(request, ipAddress)) {
            is LoginOutcomeDto.Tokens -> LoginOutcomeModel.Tokens(mapper.toTokenResponseModel(outcome.session.tokens))
            is LoginOutcomeDto.MfaRequired -> LoginOutcomeModel.MfaRequired(mfaMapper.toMfaChallengeModel(outcome.challenge))
        }

    /** `POST /auth/login/mfa` — API 로그인(비밀번호·네이티브 소셜)의 2단계. */
    fun completeMfa(request: MfaLoginRequestDto, ipAddress: String?): TokenResponseModel =
        mapper.toTokenResponseModel(
            mfaChallengeBizService.complete(request.mfaToken, MfaOrigin.API, request.method, request.code, ipAddress).session.tokens,
        )

    /** 인가 서버 로그인 화면 — 토큰 대신 브라우저 세션에 로그인시킬 principal, 또는 2단계로 넘길 challenge 토큰. */
    fun loginForBrowser(request: LoginRequestDto, ipAddress: String?): BrowserLoginOutcomeModel =
        when (val outcome = authBizService.loginForBrowser(request, ipAddress)) {
            is LoginOutcomeDto.Tokens ->
                BrowserLoginOutcomeModel.SignedIn(mapper.toBrowserPrincipalModel(authBizService.browserPrincipalOf(outcome.session)))
            is LoginOutcomeDto.MfaRequired -> BrowserLoginOutcomeModel.MfaRequired(outcome.challenge.token)
        }

    fun logout(sessionId: String) {
        authBizService.logout(sessionId)
    }

    fun refresh(refreshToken: String): AccessTokenResponseModel = mapper.toAccessTokenResponseModel(authBizService.refresh(refreshToken))

    fun changePassword(userId: Long, request: ChangePasswordRequestDto) {
        authBizService.changePassword(userId, request)
    }

    fun getCurrentUser(userId: Long): AuthUserInfoModel = mapper.toAuthUserInfoModel(authBizService.getCurrentUser(userId))

    fun logoutAllSessions(userId: Long, excludeSessionId: String?) {
        authBizService.logoutAllSessions(userId, excludeCurrentSession = excludeSessionId)
    }
}