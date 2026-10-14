package com.rex.api.pub.skeleton.exception.auth

class DuplicateUserException(
    message: String,
    cause: Throwable? = null,
) : AuthException(message, cause)