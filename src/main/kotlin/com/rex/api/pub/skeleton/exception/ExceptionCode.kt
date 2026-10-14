package com.rex.api.pub.skeleton.exception

/**
 * 예외에 대한 코드 및 치환값을 제공하는 인터페이스
 */
interface ExceptionCode {
    /**
     * 에러 메시지 코드 (다국어 지원 또는 상세 메시지 조회용)
     */
    val code: String?

    /**
     * 메시지 치환을 위한 key-value 맵
     */
    fun replaceValues(): Map<String, String>
}