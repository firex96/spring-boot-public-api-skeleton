package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.enums.ErrorCode

/**
 * 외부 API 호출 실패 시 발생하는 예외.
 *
 * 기본은 502 `EXTERNAL_API_ERROR` 다. 상대가 응답하지 못한 경우처럼 503 으로 알려야 하면
 * [errorCode] 에 `SERVICE_UNAVAILABLE_ERROR` 를 넘긴다.
 */
class ApiCallException(
    message: String = "외부 API 호출에 실패했습니다.",
    errorCode: ErrorCode = ErrorCode.EXTERNAL_API_ERROR,
    cause: Throwable? = null,
) : CodedException(errorCode, message, cause)