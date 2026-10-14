package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.dto.ErrorMessageDto

/**
 * 예외별 메시지 해결을 위한 함수형 인터페이스.
 * 다국어 지원 또는 상세 에러 메시지 제공에 사용.
 */
fun interface ErrorMessageService {
    /**
     * 예외에 대한 에러 메시지를 반환.
     *
     * @param exception 발생한 예외
     * @param defaultCode 기본 에러 코드
     * @return 에러 메시지 정보
     */
    fun getMessage(
        exception: Exception,
        defaultCode: String,
    ): ErrorMessageDto
}