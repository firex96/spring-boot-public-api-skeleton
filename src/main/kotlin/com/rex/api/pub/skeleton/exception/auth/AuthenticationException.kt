package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 인증 실패 — 401 `UNAUTHORIZED_ERROR`. */
class AuthenticationException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.UNAUTHORIZED_ERROR, message, cause)