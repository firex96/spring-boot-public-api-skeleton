package com.rex.api.pub.skeleton.exception

/**
 * 요청한 리소스를 찾을 수 없을 때 발생하는 예외
 */
open class ItemNotFoundException : RuntimeException, ExceptionCode {
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