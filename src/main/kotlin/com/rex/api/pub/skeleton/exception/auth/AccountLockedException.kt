package com.rex.api.pub.skeleton.exception.auth

/** 로그인 시도 횟수 초과로 잠긴 계정 — 423 Locked, AUTH001. */
class AccountLockedException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)