package com.rex.api.pub.skeleton.exception.auth

import com.rex.api.pub.skeleton.enums.ErrorCode

/** 로그인 시도 횟수 초과로 잠긴 계정 — 423 Locked, AUTH001. */
class AccountLockedException(
    message: String,
    cause: Throwable? = null,
) : AuthException(ErrorCode.ACCOUNT_LOCKED_ERROR, message, cause)