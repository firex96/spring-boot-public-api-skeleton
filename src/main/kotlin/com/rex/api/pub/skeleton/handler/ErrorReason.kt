package com.rex.api.pub.skeleton.handler

/**
 * 오류 응답 `reason` 의 고정 문장. 사용자에게 그대로 보여도 되는 문장만 둔다 — 내부 상세(예외 메시지·클래스 이름)는 로그로 보낸다.
 * 업무 예외 4xx 는 예외 메시지를 그대로 쓰고, 그 밖의 경우는 여기 문장을 쓴다.
 */
object ErrorReason {
    const val AUTHENTICATION_REQUIRED = "인증이 필요합니다"
    const val ACCESS_FORBIDDEN = "접근 권한이 없습니다"
    const val CONFLICT = "요청이 현재 상태와 충돌합니다."
    const val LOCKED = "요청한 대상이 잠겨 있습니다."
    const val SERVER_ERROR = "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    const val EXTERNAL_SERVICE_ERROR = "외부 서비스가 응답하지 않아 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
    const val MALFORMED_BODY = "요청 본문의 형식이 올바르지 않습니다."
    const val MISSING_BODY = "요청 본문이 없습니다."
    const val INVALID_VALUE = "요청 값이 올바르지 않습니다."
    const val PATH_NOT_FOUND = "요청한 경로를 찾을 수 없습니다."
    const val TARGET_NOT_FOUND = "요청한 대상을 찾을 수 없습니다."
    const val BAD_REQUEST = "잘못된 요청입니다."
    const val METHOD_NOT_ALLOWED = "허용되지 않는 요청 방식입니다."
    const val NOT_ACCEPTABLE = "요청한 응답 형식을 제공할 수 없습니다."
    const val PAYLOAD_TOO_LARGE = "요청 본문이 너무 큽니다."
    const val UNSUPPORTED_MEDIA_TYPE = "지원하지 않는 요청 본문 형식입니다."

    fun invalidFormat(name: String) = "'$name' 값의 형식이 올바르지 않습니다."

    fun missingParameter(name: String) = "필수 파라미터 '$name' 가 없습니다."

    fun missingHeader(name: String) = "필수 헤더 '$name' 가 없습니다."

    /** 상태 기반으로 처리한 4xx(처리하지 않는 Spring 예외)의 문장 — `ErrorCode.fromStatus` 가 고르는 4xx 상태마다 문장이 있다. */
    fun forStatus(status: Int): String =
        when (status) {
            401 -> AUTHENTICATION_REQUIRED
            403 -> ACCESS_FORBIDDEN
            404 -> PATH_NOT_FOUND
            405 -> METHOD_NOT_ALLOWED
            406 -> NOT_ACCEPTABLE
            409 -> CONFLICT
            413 -> PAYLOAD_TOO_LARGE
            415 -> UNSUPPORTED_MEDIA_TYPE
            423 -> LOCKED
            else -> BAD_REQUEST
        }

    /** 5xx 의 문장 — 상태 502~504(게이트웨이 계열)는 외부 서비스 문장, 그 밖은 일반 문장. */
    fun forServerError(status: Int): String = if (status in 502..504) EXTERNAL_SERVICE_ERROR else SERVER_ERROR
}