package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.dto.ErrorMessageDto
import org.springframework.stereotype.Service

/**
 * 기본 에러 메시지 서비스 구현체.
 * 예외 메시지를 그대로 반환하는 단순 구현.
 *
 * 다국어 지원이나 DB 기반 메시지 조회가 필요한 경우
 * 이 클래스를 확장하거나 새로운 구현체를 작성.
 */
@Service
class DefaultErrorMessageService : ErrorMessageService {
    override fun getMessage(
        exception: Exception,
        defaultCode: String,
    ): ErrorMessageDto {
        val code =
            when (exception) {
                is ExceptionCode -> exception.code ?: defaultCode
                else -> defaultCode
            }

        val message = exception.message ?: "알 수 없는 오류가 발생했습니다."

        return ErrorMessageDto(
            code = code,
            values = listOf(message),
        )
    }
}