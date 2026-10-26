package com.rex.api.pub.skeleton.handler

import io.kotest.matchers.string.shouldNotContain
import org.springframework.test.web.servlet.ResultActions

/** 오류 응답 본문에 내부 정보(패키지·예외 클래스 이름)가 없다 — [extra] 는 그 경우에만 나올 수 있는 내부 문자열이다. */
fun ResultActions.bodyHasNoInternals(vararg extra: String): ResultActions {
    val body = andReturn().response.contentAsString
    (listOf("com.rex", "Exception", "java.", "org.springframework") + extra).forEach { body shouldNotContain it }
    return this
}