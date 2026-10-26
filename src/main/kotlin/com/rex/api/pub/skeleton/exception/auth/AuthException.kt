package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.exception.CodedException

/** 인증·인가 예외의 부모. 하위 예외는 자기 [ErrorCode] 만 정하고, 응답은 `CodedException` 핸들러가 만든다. */
abstract class AuthException(
    errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : CodedException(errorCode, message, cause)