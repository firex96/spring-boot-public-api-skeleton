package com.rex.api.pub.skeleton.enums.auth

import com.rex.api.pub.skeleton.enums.StringEnum

/** 2차 인증 등록 상태 — setup 이 PENDING 으로 만들고, 첫 코드 확인(activate)이 ACTIVE 로 바꾼다. 로그인 2단계는 ACTIVE 만 요구한다. */
enum class MfaStatus(
    override val value: String,
) : StringEnum {
    PENDING("PENDING"),
    ACTIVE("ACTIVE"),
}