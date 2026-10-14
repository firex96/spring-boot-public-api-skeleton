package com.rex.api.pub.skeleton.exception.auth

class UnauthorizedException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)