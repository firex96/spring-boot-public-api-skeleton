package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 업무 계층의 권한 거부(그룹·권한 관리) — 403 `FORBIDDEN_ERROR`. */
class AuthorizationException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.FORBIDDEN_ERROR, message, cause)