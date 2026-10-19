package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import com.rex.api.pub.skeleton.converter.MfaStatusConverter
import com.rex.api.pub.skeleton.enums.auth.MfaStatus
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 사용자의 TOTP 등록 — 사용자당 하나. 시크릿은 `MfaSecretCipher` 로 암호화한 값만 둔다. */
@Entity
@Table(name = "user_mfa_credentials")
class UserMfaCredentialEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,

    @Column(name = "totp_secret_encrypted", nullable = false, length = 255)
    var totpSecretEncrypted: String,

    @Convert(converter = MfaStatusConverter::class)
    @Column(name = "status", nullable = false, length = 20)
    var status: MfaStatus,

    // 마지막으로 통과한 TOTP 구간 — 같은 구간 재사용 방지
    @Column(name = "last_used_step")
    var lastUsedStep: Long? = null,

    @Column(name = "activated_at")
    var activatedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)