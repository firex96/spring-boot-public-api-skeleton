package com.rex.api.pub.skeleton.dto.auth

data class MfaStatusDto(
    val enabled: Boolean,
    val recoveryCodesLeft: Long,
)