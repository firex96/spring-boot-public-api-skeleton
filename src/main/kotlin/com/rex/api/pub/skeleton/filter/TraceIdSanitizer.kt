package com.rex.api.pub.skeleton.filter

import java.util.UUID

/**
 * 외부에서 들어온 traceId 를 신뢰 가능한 형태로 정규화한다.
 *
 * 검증 없이 MDC 에 넣으면 외부 입력이 로그 상관관계를 오염시키고
 * (개행 주입에 의한 로그 위조 포함), 아웃바운드 전파를 타고 하위 서비스까지 번진다.
 */
object TraceIdSanitizer {
    /** UUID·W3C trace-id 등 통용 형식을 수용하는 보수적 허용 문자·길이. */
    private val SAFE_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun isValid(raw: String?): Boolean = raw != null && SAFE_PATTERN.matches(raw)

    /** 유효하면 그대로, 아니면 새 UUID 를 만든다. */
    fun sanitize(raw: String?): String = raw?.takeIf(::isValid) ?: UUID.randomUUID().toString()
}