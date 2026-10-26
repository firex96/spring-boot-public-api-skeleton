package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 토큰이 틀렸거나 만료됐다 — 401 `UNAUTHORIZED_ERROR`. */
class InvalidTokenException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.UNAUTHORIZED_ERROR, message, cause)