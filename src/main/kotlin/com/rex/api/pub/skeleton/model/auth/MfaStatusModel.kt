package com.rex.api.pub.skeleton.model.auth

import com.fasterxml.jackson.annotation.JsonProperty

data class MfaStatusModel(
    @field:JsonProperty("enabled")
    val enabled: Boolean,
    @field:JsonProperty("recoveryCodesLeft")
    val recoveryCodesLeft: Long,
)