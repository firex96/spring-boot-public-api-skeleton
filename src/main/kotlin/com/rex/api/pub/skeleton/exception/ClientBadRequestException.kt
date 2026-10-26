package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.enums.ErrorCode

/**
 * 클라이언트의 잘못된 요청 시 발생하는 예외 — 400 `BAD_REQUEST_ERROR`.
 */
open class ClientBadRequestException(
    message: String,
    cause: Throwable? = null,
) : CodedException(ErrorCode.BAD_REQUEST_ERROR, message, cause)