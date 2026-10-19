package com.rex.api.pub.skeleton.dto.auth

/** 새로 만든 복구 코드 원본 — 응답으로 한 번만 나간다(저장은 해시). */
data class MfaRecoveryCodesDto(
    val recoveryCodes: List<String>,
)