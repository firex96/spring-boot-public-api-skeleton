package com.rex.api.pub.skeleton.dto.auth

/** TOTP 등록 시작 — 인증 앱에 넣을 시크릿(Base32, 4자씩 공백)과 otpauth URI. */
data class MfaSetupDto(
    val secret: String,
    val otpauthUri: String,
)