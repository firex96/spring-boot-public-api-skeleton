package com.rex.api.pub.skeleton.exception.auth

class ForbiddenException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)