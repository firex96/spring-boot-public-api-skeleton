package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.UserMfaCredentialDto
import com.rex.api.pub.skeleton.enums.auth.MfaStatus
import com.rex.api.pub.skeleton.mapper.MfaMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserMfaCredentialEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserMfaCredentialRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserMfaRecoveryCodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * 2차 인증 엔티티 서비스 — TOTP 등록의 저장·조회·사용 표시. 재사용 방지는 조건부 UPDATE 의 갱신 행 수로 판정한다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserMfaEntityService(
    private val credentialRepository: UserMfaCredentialRepository,
    private val recoveryCodeRepository: UserMfaRecoveryCodeRepository,
    private val mapper: MfaMapper,
) {
    fun getCredential(userId: Long): UserMfaCredentialDto? =
        credentialRepository.findByUserId(userId).getOrNull()?.let { mapper.toUserMfaCredentialDto(it) }

    fun isActive(userId: Long): Boolean = credentialRepository.findByUserId(userId).getOrNull()?.status == MfaStatus.ACTIVE

    /** 로그인 관문용 — 공유 잠금으로 읽어 진행 중인 활성화와 직렬화한다. 호출자의 쓰기 트랜잭션 안에서 불러야 잠금이 세션 발급까지 이어진다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun isActiveForLogin(userId: Long): Boolean = credentialRepository.findByUserIdForShare(userId).getOrNull()?.status == MfaStatus.ACTIVE

    /** 등록 변경 작업의 시작 — 행을 잡고(FOR UPDATE) 읽는다. 같은 사용자의 다른 변경은 이 트랜잭션이 끝날 때까지 기다린다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun lockCredential(userId: Long): UserMfaCredentialDto? =
        credentialRepository.findByUserIdForUpdate(userId).getOrNull()?.let { mapper.toUserMfaCredentialDto(it) }

    /** PENDING 등록을 만들거나 기존 PENDING 을 새 시크릿으로 덮는다. ACTIVE 인지는 호출자가 먼저 확인한다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun savePending(userId: Long, totpSecretEncrypted: String) {
        val now = LocalDateTime.now()
        val existing = credentialRepository.findByUserId(userId).getOrNull()
        if (existing == null) {
            credentialRepository.save(UserMfaCredentialEntity(userId = userId, totpSecretEncrypted = totpSecretEncrypted, status = MfaStatus.PENDING))
            return
        }
        existing.totpSecretEncrypted = totpSecretEncrypted
        existing.status = MfaStatus.PENDING
        existing.lastUsedStep = null
        existing.activatedAt = null
        existing.updatedAt = now
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun activate(userId: Long) {
        val credential = credentialRepository.findByUserId(userId).getOrNull() ?: return
        val now = LocalDateTime.now()
        credential.status = MfaStatus.ACTIVE
        credential.activatedAt = now
        credential.updatedAt = now
    }

    /** TOTP 구간 [step] 을 사용했다고 표시 — 이미 그 구간(또는 그 뒤)을 썼으면 false(재사용). */
    @Transactional(transactionManager = "authTransactionManager")
    fun markTotpStepUsed(userId: Long, step: Long): Boolean = credentialRepository.markStepUsed(userId, step) == 1

    /** 등록과 복구 코드를 모두 지운다 — 해제할 때. */
    @Transactional(transactionManager = "authTransactionManager")
    fun deleteAll(userId: Long): Boolean {
        recoveryCodeRepository.deleteAllByUserIdInBulk(userId)
        return credentialRepository.deleteAllByUserIdInBulk(userId) > 0
    }
}