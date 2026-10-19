package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.auth.MfaCodeRequestDto
import com.rex.api.pub.skeleton.dto.auth.MfaVerifyRequestDto
import com.rex.api.pub.skeleton.mapper.MfaMapper
import com.rex.api.pub.skeleton.model.auth.MfaRecoveryCodesModel
import com.rex.api.pub.skeleton.model.auth.MfaSetupModel
import com.rex.api.pub.skeleton.model.auth.MfaStatusModel
import com.rex.api.pub.skeleton.service.mfa.MfaBizService
import org.springframework.stereotype.Service

/** 2차 인증 등록·관리 매핑 — `UserMfaController`. */
@Service
class UserMfaMappingService(
    private val mfaBizService: MfaBizService,
    private val mfaMapper: MfaMapper,
) {
    fun status(userId: Long): MfaStatusModel = mfaMapper.toMfaStatusModel(mfaBizService.status(userId))

    fun setup(userId: Long): MfaSetupModel = mfaMapper.toMfaSetupModel(mfaBizService.setup(userId))

    /** 응답 모델이 필드 하나라 매퍼(MapStruct 가 `copy` 를 프로퍼티로 읽는다) 대신 직접 만든다. */
    fun activate(userId: Long, request: MfaCodeRequestDto, currentSessionId: String): MfaRecoveryCodesModel =
        MfaRecoveryCodesModel(recoveryCodes = mfaBizService.activate(userId, request.code, currentSessionId).recoveryCodes)

    fun regenerateRecoveryCodes(userId: Long, request: MfaCodeRequestDto): MfaRecoveryCodesModel =
        MfaRecoveryCodesModel(recoveryCodes = mfaBizService.regenerateRecoveryCodes(userId, request.code).recoveryCodes)

    fun disable(userId: Long, request: MfaVerifyRequestDto) {
        mfaBizService.disable(userId, request.method, request.code)
    }
}