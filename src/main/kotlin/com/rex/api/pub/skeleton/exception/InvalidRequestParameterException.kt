package com.rex.api.pub.skeleton.exception

/**
 * 요청 파라미터가 유효하지 않을 때 발생하는 예외 — 400 `BAD_REQUEST_ERROR`.
 */
open class InvalidRequestParameterException(
    message: String = "유효하지 않은 요청 파라미터 입니다.",
) : ClientBadRequestException(message)