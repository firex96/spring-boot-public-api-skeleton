package com.rex.api.pub.skeleton.exception.auth

class AuthenticationException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)