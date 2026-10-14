package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.context.AuditorContext
import org.springframework.core.task.TaskDecorator

/**
 * 감사 주체를 비동기 스레드로 전파하는 TaskDecorator.
 *
 * [MdcTaskDecorator] 와 나란히 쓰인다 — 로깅 컨텍스트와 감사 주체는 채널이 다르고,
 * 전파도 각자 결정한다.
 *
 * **캡처는 제출 스레드에서, 복원·정리는 워커 스레드에서** 일어난다.
 *
 * 두 지점이 핵심이다.
 * - `set(captured)` 가 **`null` 도 반영**한다 — 컨텍스트 없이 제출한 작업이
 *   앞 작업의 사용자 ID 를 물려받으면 **틀린 감사 값이 DB 에 저장된다.**
 * - `finally` 에서 지운다 — 워커 스레드에 값이 남으면 다음 작업이 오염된다.
 */
class AuditorContextTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val captured = AuditorContext.get()
        return Runnable {
            AuditorContext.set(captured)
            try {
                runnable.run()
            } finally {
                AuditorContext.clear()
            }
        }
    }
}