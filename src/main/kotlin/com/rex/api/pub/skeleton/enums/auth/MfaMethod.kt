package com.rex.api.pub.skeleton.enums.auth

import com.fasterxml.jackson.annotation.JsonValue
import com.rex.api.pub.skeleton.enums.StringEnum

/** 2단계 코드의 종류 — 요청 본문·폼의 `method` 값(`totp`). */
enum class MfaMethod(
    @JsonValue
    override val value: String,
) : StringEnum {
    TOTP("totp"),
}