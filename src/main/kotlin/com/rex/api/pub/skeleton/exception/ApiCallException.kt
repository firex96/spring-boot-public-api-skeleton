package com.rex.api.pub.skeleton.exception

import org.springframework.http.HttpStatus

/**
 * 외부 API 호출 실패 시 발생하는 예외
 */
class ApiCallException : RuntimeException {
    val httpStatus: HttpStatus

    constructor() : super("외부 API 호출에 실패했습니다.") {
        this.httpStatus = HttpStatus.BAD_GATEWAY
    }

    constructor(message: String) : super(message) {
        this.httpStatus = HttpStatus.BAD_GATEWAY
    }

    constructor(message: String, httpStatus: HttpStatus) : super(message) {
        this.httpStatus = httpStatus
    }

    constructor(message: String, httpStatus: HttpStatus, cause: Throwable) : super(message, cause) {
        this.httpStatus = httpStatus
    }
}