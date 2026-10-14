package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.model.auth.AccessTokenResponseModel
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import org.springframework.stereotype.Service

/** 인증 API 매핑 — `AuthController`·`UserController` 의 DTO → BizService → Model. */
@Service
class AuthMappingService(
    private val authBizService: AuthBizService,
    private val mapper: AuthMapper,
) {
    fun signup(request: SignupRequestDto): AuthUserInfoModel = mapper.toAuthUserInfoModel(authBizService.signup(request))

    fun login(request: LoginRequestDto, ipAddress: String?): TokenResponseModel =
        mapper.toTokenResponseModel(authBizService.login(request, ipAddress))

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