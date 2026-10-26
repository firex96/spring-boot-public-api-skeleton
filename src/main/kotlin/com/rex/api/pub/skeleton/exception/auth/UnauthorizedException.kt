package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 인증 정보 없음·무효 — 401 `UNAUTHORIZED_ERROR`. */
class UnauthorizedException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.UNAUTHORIZED_ERROR, message, cause)