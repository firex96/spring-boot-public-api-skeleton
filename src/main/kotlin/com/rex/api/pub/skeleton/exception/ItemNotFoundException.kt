package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.enums.ErrorCode

/**
 * 요청한 리소스를 찾을 수 없을 때 발생하는 예외 — 404 `NOT_FOUND_ERROR`.
 */
open class ItemNotFoundException(
    message: String,
    cause: Throwable? = null,
) : CodedException(ErrorCode.NOT_FOUND_ERROR, message, cause)