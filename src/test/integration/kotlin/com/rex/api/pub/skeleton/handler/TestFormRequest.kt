package com.rex.api.pub.skeleton.handler

/** 쿼리 파라미터를 `@ModelAttribute` 로 받는 요청 — 숫자 필드에 문자열을 보내면 바인딩 실패(타입 변환 실패)가 난다. */
data class TestFormRequest(
    val size: Int?,
)