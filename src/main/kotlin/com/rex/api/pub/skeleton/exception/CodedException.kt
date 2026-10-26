package com.rex.api.pub.skeleton.exception

import com.rex.api.pub.skeleton.enums.ErrorCode

/**
 * 상태·구분 코드를 [errorCode] 하나가 정하는 예외의 부모. `GlobalExceptionHandler` 가 이 타입 하나로 받는다.
 *
 * 새 업무 코드가 필요하면 `ErrorCode` 에 항목을 추가하고 `throw CodedException(ErrorCode.X)` 로 던진다.
 * 같은 코드를 여러 곳에서 던지거나 `catch` 로 구분해야 할 때만 하위 클래스를 만든다.
 * [message] 는 4xx 응답의 `reason` 으로 나가므로 사용자에게 보여도 되는 문장으로 쓴다.
 */
open class CodedException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)