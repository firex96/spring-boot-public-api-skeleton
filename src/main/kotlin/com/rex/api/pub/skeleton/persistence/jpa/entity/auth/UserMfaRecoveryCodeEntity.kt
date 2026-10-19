package com.rex.api.pub.skeleton.persistence.jpa.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 1회용 복구 코드 — SHA-256 해시(`TokenHasher.hash`)만 둔다. 사용하면 `used_at`. */
@Entity
@Table(name = "user_mfa_recovery_codes")
class UserMfaRecoveryCodeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    // 유일성은 (user_id, code_hash) — 40비트 코드는 사용자 사이 해시가 겹칠 수 있다
    @Column(name = "code_hash", nullable = false, length = 64)
    var codeHash: String,

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)