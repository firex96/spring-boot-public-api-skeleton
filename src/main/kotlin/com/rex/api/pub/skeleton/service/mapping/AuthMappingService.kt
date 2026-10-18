package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.model.auth.AccessTokenResponseModel
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.model.auth.BrowserPrincipalModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.auth.TokenHandoffBizService
import org.springframework.stereotype.Service

/** 인증 API 매핑 — `AuthController`·`UserController` 의 DTO → BizService → Model. */
@Service
class AuthMappingService(
    private val authBizService: AuthBizService,
    private val tokenHandoffBizService: TokenHandoffBizService,
    private val mapper: AuthMapper,
) {
    /** 소셜 콜백이 맡겨 둔 토큰을 한 번만 꺼낸다 — 없거나 이미 꺼냈으면 null. */
    fun takeTokenHandoff(userSessionId: String): TokenResponseModel? = tokenHandoffBizService.take(userSessionId)?.let { mapper.toTokenResponseModel(it) }

    fun signup(request: SignupRequestDto): AuthUserInfoModel = mapper.toAuthUserInfoModel(authBizService.signup(request))

    fun login(request: LoginRequestDto, ipAddress: String?): TokenResponseModel =
        mapper.toTokenResponseModel(authBizService.login(request, ipAddress))

    /** 인가 서버 로그인 화면 — 토큰 대신 브라우저 세션에 로그인시킬 principal 을 돌려준다. */
    fun loginForBrowser(request: LoginRequestDto, ipAddress: String?): BrowserPrincipalModel =
        mapper.toBrowserPrincipalModel(authBizService.loginForBrowser(request, ipAddress))

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