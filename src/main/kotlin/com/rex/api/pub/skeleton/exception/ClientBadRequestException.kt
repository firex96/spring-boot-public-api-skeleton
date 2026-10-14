package com.rex.api.pub.skeleton.exception

/**
 * 클라이언트의 잘못된 요청 시 발생하는 예외
 */
open class ClientBadRequestException : RuntimeException, ExceptionCode {
    override var code: String? = null
    private var replaceValues: Map<String, String> = mapOf()

    constructor(message: String?) : super(message)

    constructor(code: String?, defaultMessage: String?) : super(defaultMessage) {
        this.code = code
    }

    constructor(code: String?, defaultMessage: String?, replaceValues: Map<String, String>) : super(defaultMessage) {
        this.code = code
        this.replaceValues = replaceValues
    }

    override fun replaceValues(): Map<String, String> = this.replaceValues
}