package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.enums.ErrorCode
import org.springframework.web.reactive.function.client.WebClientRequestException

/**
 * 외부 API 호출 실패 시 발생하는 예외.
 *
 * 기본은 502 `EXTERNAL_API_ERROR` 다. 상대에게 연결하지 못한 경우(연결 거부·타임아웃·DNS)는 503 `SERVICE_UNAVAILABLE_ERROR` 다 —
 * 호출 실패를 감쌀 때는 [from] 을 써서 이 판정을 클라이언트마다 다시 짜지 않는다.
 * 5xx 라 [message] 는 응답에 나가지 않고 로그에만 남는다. 원인 예외의 상세는 [cause] 로 넘긴다.
 */
class ApiCallException(
    message: String = "외부 API 호출에 실패했습니다.",
    errorCode: ErrorCode = ErrorCode.EXTERNAL_API_ERROR,
    cause: Throwable? = null,
) : CodedException(errorCode, message, cause) {
    companion object {
        /** 원인 사슬에 연결 실패([WebClientRequestException])가 있으면 503, 아니면 502 로 감싼다 — `WebClientUtil` 이 감싼 예외도 따라간다. */
        fun from(cause: Throwable): ApiCallException {
            val unreachable = generateSequence(cause) { it.cause }.any { it is WebClientRequestException }
            return ApiCallException(
                errorCode = if (unreachable) ErrorCode.SERVICE_UNAVAILABLE_ERROR else ErrorCode.EXTERNAL_API_ERROR,
                cause = cause,
            )
        }
    }
}