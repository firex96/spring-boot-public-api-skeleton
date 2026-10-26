package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 권한 없음 — 403 `FORBIDDEN_ERROR`. */
class ForbiddenException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.FORBIDDEN_ERROR, message, cause)