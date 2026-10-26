package com.rex.api.pub.skeleton.enums

/*
[공통 코드] API 통신에 대한 '에러 코드'를 Enum 형태로 관리를 한다.
Global Error CodeList : 전역으로 발생하는 에러코드를 관리한다.
Custom Error CodeList : 업무 페이지에서 발생하는 에러코드를 관리한다
*/
enum class ErrorCode(
    val status: Int, // HTTP 응답 상태 — GlobalExceptionHandler 가 이 값으로만 응답 상태를 정한다.
    val divisionCode: String,
    val message: String,
) {
    /**
     * ******************************* Global Error CodeList ***************************************
     * HTTP Status Code
     * 400 : Bad Request
     * 401 : Unauthorized
     * 403 : Forbidden
     * 404 : Not Found
     * 405 : Method Not Allowed
     * 406 : Not Acceptable
     * 413 : Payload Too Large
     * 415 : Unsupported Media Type
     * 500 : Internal Server Error
     * 502 : Bad Gateway
     * 503 : Service Unavailable
     * *********************************************************************************************
     *
     */
    BAD_REQUEST_ERROR(400, "G001", "Bad Request"),
    // 잘못된 서버 요청

    // @RequestBody 데이터 미 존재
    REQUEST_BODY_MISSING_ERROR(400, "G002", "Request Body Missing"),

    // 유효하지 않은 타입
    INVALID_TYPE_VALUE(400, "G003", "Invalid Type Value"),

    // Request Parameter 로 데이터가 전달되지 않을 경우
    MISSING_REQUEST_PARAMETER_ERROR(400, "G004", "Missing Request Parameter"),

    // 서버 측 입출력 실패
    IO_ERROR(500, "G005", "I/O Error"),

    // com.google.gson JSON 파싱 실패
    JSON_PARSE_ERROR(400, "G006", "Malformed JSON"),

    // tools.jackson.core Processing Error
    JACKSON_PROCESS_ERROR(400, "G007", "Malformed Request Body"),

    // 인증 실패
    UNAUTHORIZED_ERROR(401, "G015", "Unauthorized"),

    // 계정 잠금 (로그인 시도 횟수 초과) — docs/ERROR_CODES.md 「AccountLockedException」 423 / AUTH001
    ACCOUNT_LOCKED_ERROR(423, "AUTH001", "Account Locked"),

    // 2차 인증 — docs/ERROR_CODES.md 「MfaException」
    MFA_INVALID_CODE(400, "MFA001", "Invalid MFA Code"),
    MFA_CHALLENGE_INVALID(401, "MFA002", "Invalid Or Expired MFA Challenge"),
    MFA_ALREADY_ENABLED(409, "MFA003", "MFA Already Enabled"),
    MFA_NOT_ENABLED(409, "MFA004", "MFA Not Enabled"),
    MFA_SETUP_NOT_FOUND(404, "MFA005", "MFA Setup Not Found"),
    MFA_TOO_MANY_ATTEMPTS(429, "MFA006", "Too Many MFA Attempts"),

    // 권한이 없음
    FORBIDDEN_ERROR(403, "G008", "Forbidden"),

    // 리소스 충돌 (중복 등)
    CONFLICT_ERROR(409, "G013", "Conflict"),

    // 서버로 요청한 리소스가 존재하지 않음
    NOT_FOUND_ERROR(404, "G009", "Not Found"),

    // NULL Point Exception 발생
    NULL_POINT_ERROR(500, "G010", "Internal Server Error"),

    // @RequestBody 및 @RequestParam, @PathVariable 값이 유효하지 않음
    NOT_VALID_ERROR(400, "G011", "One or more validation errors occurred"),

    // 필수 Header 값이 없거나 유효하지 않음
    NOT_VALID_HEADER_ERROR(400, "G012", "Missing Or Invalid Header"),

    // 외부 API 가 오류로 응답했거나 호출이 실패함
    EXTERNAL_API_ERROR(502, "G014", "Bad Gateway"),

    // 외부 API 에 연결하지 못함 (연결 거부·타임아웃·DNS)
    SERVICE_UNAVAILABLE_ERROR(503, "G016", "Service Unavailable"),

    // 아래 넷은 처리하지 않는 Spring 예외(ErrorResponse)를 그 상태로 응답할 때 쓴다 — fromStatus
    METHOD_NOT_ALLOWED_ERROR(405, "G017", "Method Not Allowed"),
    NOT_ACCEPTABLE_ERROR(406, "G018", "Not Acceptable"),
    PAYLOAD_TOO_LARGE_ERROR(413, "G019", "Payload Too Large"),
    UNSUPPORTED_MEDIA_TYPE_ERROR(415, "G020", "Unsupported Media Type"),

    // 서버가 처리 할 방법을 모르는 경우 발생
    INTERNAL_SERVER_ERROR(500, "G999", "Internal Server Error"),

    /******************************** Custom Error CodeList ****************************************/
    // 업무 코드는 여기에 추가하고 `throw CodedException(ErrorCode.X)` 로 던진다. status 가 곧 HTTP 응답 상태다.
    ;

    companion object {
        /** 상태 기반 처리의 대표 코드 — 첫 일치가 아니라 이 목록이 정한다(400 은 BAD_REQUEST_ERROR). 상태가 겹치면 초기화가 실패한다. */
        private val BY_STATUS: Map<Int, ErrorCode> =
            listOf(
                BAD_REQUEST_ERROR,
                UNAUTHORIZED_ERROR,
                FORBIDDEN_ERROR,
                NOT_FOUND_ERROR,
                METHOD_NOT_ALLOWED_ERROR,
                NOT_ACCEPTABLE_ERROR,
                CONFLICT_ERROR,
                PAYLOAD_TOO_LARGE_ERROR,
                UNSUPPORTED_MEDIA_TYPE_ERROR,
                ACCOUNT_LOCKED_ERROR,
                INTERNAL_SERVER_ERROR,
                EXTERNAL_API_ERROR,
                SERVICE_UNAVAILABLE_ERROR,
            ).let { codes -> codes.associateBy { it.status }.also { check(it.size == codes.size) { "BY_STATUS 에 같은 상태의 코드가 둘 있다" } } }

        /**
         * 상태에 맞는 코드. 목록 밖 상태는 4xx → [BAD_REQUEST_ERROR], 5xx → [INTERNAL_SERVER_ERROR] 이고,
         * 응답 HTTP 상태도 그 코드의 상태가 된다 — 상태와 코드가 어긋난 응답은 나가지 않는다.
         */
        fun fromStatus(status: Int): ErrorCode = BY_STATUS[status] ?: if (status >= 500) INTERNAL_SERVER_ERROR else BAD_REQUEST_ERROR
    }
}