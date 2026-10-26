package com.rex.api.pub.skeleton.handler

import com.fasterxml.jackson.annotation.JsonProperty

/** non-null 필드만 가진 요청 — `{}` 를 보내면 Kotlin 생성자 실패(Jackson 오류)가 난다. 응답 문구에 이 클래스 이름이 나가면 안 된다. */
data class TestStrictRequest(
    @field:JsonProperty("login_id")
    val loginId: String,
)