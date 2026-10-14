package com.rex.api.pub.skeleton.exception.auth

open class AuthException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)