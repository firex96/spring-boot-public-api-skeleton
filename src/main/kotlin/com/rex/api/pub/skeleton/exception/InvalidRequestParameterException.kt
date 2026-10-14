package com.rex.api.pub.skeleton.exception

/**
 * 요청 파라미터가 유효하지 않을 때 발생하는 예외
 */
open class InvalidRequestParameterException : ClientBadRequestException {
    constructor() : super("유효하지 않은 요청 파라미터 입니다.")

    constructor(message: String?) : super(message)

    constructor(code: String?, defaultMessage: String?) : super(code, defaultMessage)

    constructor(code: String?, defaultMessage: String?, replaceValues: Map<String, String>) : super(
        code,
        defaultMessage,
        replaceValues,
    )
}