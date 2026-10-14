package com.rex.api.pub.skeleton.context

/**
 * 감사 주체(사용자 ID)를 담는 요청 범위 채널.
 *
 * **MDC 와 분리한다.** MDC 는 로깅 컨텍스트라 비어도 로그가 밋밋해질 뿐이지만,
 * 감사 주체가 비면 조용히 시스템 사용자로 떨어져 **틀린 값이 DB 에 남는다.**
 * 실패가 로그가 아니라 데이터에 나타나므로 채널을 나눈다.
 *
 * 채우는 지점은 레포마다 다르다 — 인증 수단이 다르기 때문이다.
 * 읽는 지점은 [com.rex.api.pub.skeleton.persistence.jpa.entity.backbone.SimpleAuditorAware] 하나뿐이다.
 *
 * **반드시 `finally` 에서 [clear] 한다.** 스레드가 재사용되면 다음 요청이
 * 남의 ID 로 감사된다.
 */
object AuditorContext {
    private val userId = ThreadLocal<Long?>()

    /** `null` 이면 지운다 — 앞 값이 남지 않게. */
    fun set(value: Long?) {
        if (value == null) userId.remove() else userId.set(value)
    }

    fun get(): Long? = userId.get()

    fun clear() = userId.remove()
}