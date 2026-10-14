package com.rex.api.pub.skeleton.exception.auth

class DuplicateResourceException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)