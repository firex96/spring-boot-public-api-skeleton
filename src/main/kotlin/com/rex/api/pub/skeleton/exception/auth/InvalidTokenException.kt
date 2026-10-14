package com.rex.api.pub.skeleton.exception.auth

class InvalidTokenException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)