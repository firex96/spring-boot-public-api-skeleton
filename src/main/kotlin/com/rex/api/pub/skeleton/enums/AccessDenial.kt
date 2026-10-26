package com.rex.api.pub.skeleton.enums

import com.rex.api.pub.skeleton.handler.ErrorReason

/**
 * 접근 거부 응답의 코드·문장 짝. 필터 체인(`SecurityConfiguration` 의 진입점·거부 핸들러)과 `GlobalExceptionHandler` 가 이것만 쓴다 —
 * 같은 401·403 이 어느 계층에서 거부됐느냐에 따라 코드나 문장이 갈리지 않게 짝을 한 곳에 둔다. 문장 값은 [ErrorReason] 의 상수다
 * (상태 기반 처리의 401·403 문장과 같은 값).
 */
enum class AccessDenial(
    val errorCode: ErrorCode,
    val reason: String,
) {
    AUTHENTICATION_REQUIRED(ErrorCode.UNAUTHORIZED_ERROR, ErrorReason.AUTHENTICATION_REQUIRED),
    ACCESS_FORBIDDEN(ErrorCode.FORBIDDEN_ERROR, ErrorReason.ACCESS_FORBIDDEN),
}