package com.rex.api.pub.skeleton.model.security

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 보안 설정 Model
 */
data class SecuritySettingsModel(
    @field:JsonProperty("enableBruteForceProtection")
    val enableBruteForceProtection: Boolean,
    @field:JsonProperty("maxLoginAttempts")
    val maxLoginAttempts: Int,
    @field:JsonProperty("loginAttemptWindowMinutes")
    val loginAttemptWindowMinutes: Int,
    @field:JsonProperty("accountLockDurationMinutes")
    val accountLockDurationMinutes: Int,
    @field:JsonProperty("enableIpBlocking")
    val enableIpBlocking: Boolean,
    @field:JsonProperty("maxIpLoginAttempts")
    val maxIpLoginAttempts: Int,
    @field:JsonProperty("ipBlockDurationMinutes")
    val ipBlockDurationMinutes: Int,
    @field:JsonProperty("permanentBlockThreshold")
    val permanentBlockThreshold: Int,
)