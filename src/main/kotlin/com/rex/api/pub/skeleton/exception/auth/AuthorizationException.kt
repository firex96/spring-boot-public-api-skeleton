package com.rex.api.pub.skeleton.exception.auth

class AuthorizationException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)