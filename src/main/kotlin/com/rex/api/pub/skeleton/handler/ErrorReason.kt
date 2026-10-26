package com.rex.api.pub.skeleton.handler

import com.rex.api.pub.skeleton.enums.ErrorCode

/**
 * 오류 응답 `reason` 의 고정 문장. 사용자에게 그대로 보여도 되는 문장만 둔다 — 내부 상세(예외 메시지·클래스 이름)는 로그로 보낸다.
 * 업무 예외 4xx 는 예외 메시지를 그대로 쓰고, 그 밖의 경우는 여기 문장을 쓴다.
 */
object ErrorReason {
    const val UNAUTHORIZED = "인증이 필요합니다"
    const val FORBIDDEN = "접근 권한이 없습니다"
    const val SERVER_ERROR = "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    const val EXTERNAL_SERVICE_ERROR = "외부 서비스가 응답하지 않아 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
    const val MALFORMED_BODY = "요청 본문의 형식이 올바르지 않습니다."
    const val MISSING_BODY = "요청 본문이 없습니다."
    const val INVALID_VALUE = "요청 값이 올바르지 않습니다."
    const val PATH_NOT_FOUND = "요청한 경로를 찾을 수 없습니다."
    const val TARGET_NOT_FOUND = "요청한 대상을 찾을 수 없습니다."
    const val BAD_REQUEST = "잘못된 요청입니다."

    fun invalidFormat(name: String) = "'$name' 값의 형식이 올바르지 않습니다."

    fun missingParameter(name: String) = "필수 파라미터 '$name' 가 없습니다."

    fun missingHeader(name: String) = "필수 헤더 '$name' 가 없습니다."

    /** 5xx 의 응답 문장 — 외부 API 계열 코드는 외부 서비스 문장, 그 밖은 일반 문장. */
    fun forServerError(errorCode: ErrorCode): String =
        if (errorCode == ErrorCode.EXTERNAL_API_ERROR || errorCode == ErrorCode.SERVICE_UNAVAILABLE_ERROR) EXTERNAL_SERVICE_ERROR else SERVER_ERROR
}