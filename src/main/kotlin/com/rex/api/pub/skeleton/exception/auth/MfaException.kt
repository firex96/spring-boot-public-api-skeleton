package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 2차 인증 실패 — 상태·구분 코드는 [errorCode](`MFA001`~`MFA006`)가 정한다. */
class MfaException(
    val errorCode: ErrorCode,
    message: String,
) : AuthException(message)