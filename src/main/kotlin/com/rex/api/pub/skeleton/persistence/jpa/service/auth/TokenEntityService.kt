package com.rex.api.pub.skeleton.persistence.jpa.service.auth

import com.rex.api.pub.skeleton.dto.auth.EmailVerificationTokenDto
import com.rex.api.pub.skeleton.dto.auth.PasswordResetTokenDto
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.EmailVerificationTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.PasswordResetTokenEntity
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.EmailVerificationTokenRepository
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.PasswordResetTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * 토큰 엔티티 서비스 — 비밀번호 재설정 토큰 및 이메일 인증 토큰. 조회는 DTO, 변경은 의도 메서드(생성·사용 처리·일괄 무효화).
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class TokenEntityService(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val mapper: AuthMapper,
) {
    // ===== PasswordResetToken =====

    fun getUnusedPasswordResetToken(tokenHash: String): PasswordResetTokenDto? =
        passwordResetTokenRepository.findByTokenHashAndIsUsedFalse(tokenHash).getOrNull()?.let { mapper.toPasswordResetTokenDto(it) }

    @Transactional(transactionManager = "authTransactionManager")
    fun createPasswordResetToken(userId: Long, tokenHash: String, expiresAt: LocalDateTime): PasswordResetTokenDto =
        mapper.toPasswordResetTokenDto(
            passwordResetTokenRepository.save(PasswordResetTokenEntity(userId = userId, tokenHash = tokenHash, expiresAt = expiresAt)),
        )

    @Transactional(transactionManager = "authTransactionManager")
    fun markPasswordResetTokenUsed(id: Long) {
        val token = passwordResetTokenRepository.findById(id).getOrNull() ?: return
        token.markAsUsed()
        passwordResetTokenRepository.save(token)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun invalidateAllPasswordResetTokensByUserId(userId: Long): Int =
        passwordResetTokenRepository.invalidateAllByUserId(userId)

    // ===== EmailVerificationToken =====

    fun getUnverifiedEmailVerificationToken(tokenHash: String): EmailVerificationTokenDto? =
        emailVerificationTokenRepository.findByTokenHashAndIsVerifiedFalse(tokenHash).getOrNull()?.let { mapper.toEmailVerificationTokenDto(it) }

    @Transactional(transactionManager = "authTransactionManager")
    fun createEmailVerificationToken(userId: Long, email: String, tokenHash: String, expiresAt: LocalDateTime): EmailVerificationTokenDto =
        mapper.toEmailVerificationTokenDto(
            emailVerificationTokenRepository.save(
                EmailVerificationTokenEntity(userId = userId, email = email, tokenHash = tokenHash, expiresAt = expiresAt),
            ),
        )

    /** `email_verified` 의 정본 — 현재 이메일을 링크로 인증한 기록이 있는가. 이메일이 없으면 false. */
    fun existsVerifiedEmail(userId: Long, email: String?): Boolean =
        email != null && emailVerificationTokenRepository.existsByUserIdAndEmailAndVerifiedAtIsNotNull(userId, email)

    @Transactional(transactionManager = "authTransactionManager")
    fun markEmailVerificationTokenVerified(id: Long) {
        val token = emailVerificationTokenRepository.findById(id).getOrNull() ?: return
        token.markAsVerified()
        emailVerificationTokenRepository.save(token)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun invalidateAllEmailVerificationTokensByUserId(userId: Long): Int =
        emailVerificationTokenRepository.invalidateAllByUserId(userId)
}