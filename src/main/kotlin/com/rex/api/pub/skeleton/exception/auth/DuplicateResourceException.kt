package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 이미 있는 리소스 — 409 `CONFLICT_ERROR`. */
class DuplicateResourceException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.CONFLICT_ERROR, message, cause)