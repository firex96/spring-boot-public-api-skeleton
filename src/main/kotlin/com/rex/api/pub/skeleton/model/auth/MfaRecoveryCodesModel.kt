package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

/** 복구 코드 원본 — 활성화·재발급 응답에서 한 번만 나간다(저장은 해시). */
data class MfaRecoveryCodesModel(
    @field:JsonProperty("recoveryCodes")
    val recoveryCodes: List<String>,
)