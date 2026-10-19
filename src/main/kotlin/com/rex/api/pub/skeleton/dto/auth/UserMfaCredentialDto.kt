package com.rex.api.pub.skeleton.dto.auth

import com.rex.api.pub.skeleton.enums.auth.MfaStatus
import java.time.LocalDateTime

/** TOTP 등록 내부 DTO — 암호화된 시크릿 포함. BizService 까지만 올라간다. */
data class UserMfaCredentialDto(
    val id: Long,
    val userId: Long,
    val totpSecretEncrypted: String,
    val status: MfaStatus,
    val lastUsedStep: Long?,
    val activatedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)