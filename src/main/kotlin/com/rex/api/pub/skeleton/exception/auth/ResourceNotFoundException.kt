package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 인증 영역의 리소스 없음 — 404 `NOT_FOUND_ERROR`. */
class ResourceNotFoundException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.NOT_FOUND_ERROR, message, cause)