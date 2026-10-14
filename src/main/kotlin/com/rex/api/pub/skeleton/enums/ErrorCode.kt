package com.rex.api.pub.skeleton.enums

/*
[공통 코드] API 통신에 대한 '에러 코드'를 Enum 형태로 관리를 한다.
Global Error CodeList : 전역으로 발생하는 에러코드를 관리한다.
Custom Error CodeList : 업무 페이지에서 발생하는 에러코드를 관리한다
*/
enum class ErrorCode(
    val status: Int, // 에러 코드의 '코드간 구분 값'을 반환한다.
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
     * 500 : Internal Server Error
     * *********************************************************************************************
     *
     */
    BAD_REQUEST_ERROR(400, "G001", "Bad Request Exception"),
    // 잘못된 서버 요청

    // @RequestBody 데이터 미 존재
    REQUEST_BODY_MISSING_ERROR(400, "G002", "Required request body is missing"),

    // 유효하지 않은 타입
    INVALID_TYPE_VALUE(400, "G003", " Invalid Type Value"),

    // Request Parameter 로 데이터가 전달되지 않을 경우
    MISSING_REQUEST_PARAMETER_ERROR(400, "G004", "Missing Servlet RequestParameter Exception"),

    // 입력/출력 값이 유효하지 않음
    IO_ERROR(400, "G005", "I/O Exception"),

    // com.google.gson JSON 파싱 실패
    JSON_PARSE_ERROR(400, "G006", "JsonParseException"),

    // tools.jackson.core Processing Error
    JACKSON_PROCESS_ERROR(400, "G007", "tools.jackson.core Exception"),

    // 인증 실패
    UNAUTHORIZED_ERROR(401, "G007A", "Unauthorized Exception"),

    // 계정 잠금 (로그인 시도 횟수 초과) — docs/ERROR_CODES.md 「AccountLockedException」 423 / AUTH001
    ACCOUNT_LOCKED_ERROR(423, "AUTH001", "Account Locked Exception"),

    // 권한이 없음
    FORBIDDEN_ERROR(403, "G008", "Forbidden Exception"),

    // 리소스 충돌 (중복 등)
    CONFLICT_ERROR(409, "G008A", "Conflict Exception"),

    // 서버로 요청한 리소스가 존재하지 않음
    NOT_FOUND_ERROR(404, "G009", "Not Found Exception"),

    // NULL Point Exception 발생
    NULL_POINT_ERROR(404, "G010", "Null Point Exception"),

    // @RequestBody 및 @RequestParam, @PathVariable 값이 유효하지 않음
    NOT_VALID_ERROR(404, "G011", "handle Validation Exception"),

    // @RequestBody 및 @RequestParam, @PathVariable 값이 유효하지 않음
    NOT_VALID_HEADER_ERROR(404, "G012", "Header에 데이터가 존재하지 않는 경우 "),

    // 서버가 처리 할 방법을 모르는 경우 발생
    INTERNAL_SERVER_ERROR(500, "G999", "Internal Server Error Exception"),

    /******************************** Custom Error CodeList ****************************************/
    INSERT_ERROR(200, "9999", "Insert Transaction Error Exception"),
    // Transaction Insert Error

    // Transaction Update Error
    UPDATE_ERROR(200, "9999", "Update Transaction Error Exception"),

    // Transaction Delete Error
    DELETE_ERROR(200, "9999", "Delete Transaction Error Exception"),
}