package com.rex.api.pub.skeleton.service.mfa

import com.rex.api.pub.skeleton.config.security.MfaProperties
import com.rex.api.pub.skeleton.config.security.MfaSecretCipher
import com.rex.api.pub.skeleton.dto.auth.MfaRecoveryCodesDto
import com.rex.api.pub.skeleton.dto.auth.MfaSetupDto
import com.rex.api.pub.skeleton.dto.auth.MfaStatusDto
import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.enums.auth.MfaMethod
import com.rex.api.pub.skeleton.enums.auth.MfaStatus
import com.rex.api.pub.skeleton.exception.auth.MfaException
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserMfaEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.UserSessionRevocationBizService
import com.rex.api.pub.skeleton.util.Base32
import com.rex.api.pub.skeleton.util.TokenHasher
import com.rex.api.pub.skeleton.util.Totp
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Clock

/**
 * 2차 인증(TOTP + 복구 코드) — 등록·해제·복구 코드 재발급과 코드 검증. 로그인 2단계(`MfaChallengeBizService`)도 코드 검증은 여기 [verifyCode] 를 쓴다.
 *
 * 같은 TOTP 구간의 코드와 같은 복구 코드는 한 번만 통과한다(DB 조건부 UPDATE). 등록·관리 API 의 재인증은 비밀번호가 아니라 현재 코드다 —
 * 소셜 전용 사용자는 비밀번호가 없다. 관리 API 의 실패는 로그인 잠금과 별개인 사용자별 카운터로 제한한다(세션 탈취 뒤 추측 방지).
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class MfaBizService(
    private val userMfaEntityService: UserMfaEntityService,
    private val userEntityService: UserEntityService,
    private val userSessionRevocationBizService: UserSessionRevocationBizService,
    private val mfaSecretCipher: MfaSecretCipher,
    private val mfaProperties: MfaProperties,
    private val auditBizService: AuditBizService,
    private val redisTemplate: StringRedisTemplate,
    private val tokenHasher: TokenHasher,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun isEnabled(userId: Long): Boolean = userMfaEntityService.isActive(userId)

    /** 로그인 관문의 활성 판정 — 공유 잠금 읽기로 진행 중인 활성화와 직렬화한다(`UserMfaEntityService.isActiveForLogin`). */
    @Transactional(transactionManager = "authTransactionManager")
    fun isEnabledForLogin(userId: Long): Boolean = userMfaEntityService.isActiveForLogin(userId)

    fun status(userId: Long): MfaStatusDto =
        isEnabled(userId).let { enabled ->
            MfaStatusDto(enabled = enabled, recoveryCodesLeft = if (enabled) userMfaEntityService.countUnusedRecoveryCodes(userId) else 0)
        }

    /** 로그인 2단계에서 쓸 수 있는 수단 — TOTP 와, 남은 복구 코드가 있으면 복구 코드. */
    fun availableMethods(userId: Long): List<MfaMethod> =
        if (userMfaEntityService.countUnusedRecoveryCodes(userId) > 0) listOf(MfaMethod.TOTP, MfaMethod.RECOVERY) else listOf(MfaMethod.TOTP)

    /** 등록 시작 — 새 시크릿을 PENDING 으로 둔다(이전 PENDING 은 대체). 첫 코드를 [activate] 로 확인해야 켜진다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun setup(userId: Long): MfaSetupDto {
        // 행을 잡은 뒤 상태를 본다 — 진행 중인 activate 가 커밋하면 여기서 ACTIVE 를 보고 멈춘다(늦은 setup 이 켜진 등록을 덮지 않게)
        if (userMfaEntityService.lockCredential(userId)?.status == MfaStatus.ACTIVE) {
            throw MfaException(ErrorCode.MFA_ALREADY_ENABLED, "2차 인증이 이미 켜져 있습니다")
        }
        val loginId = userEntityService.getUserById(userId)?.loginId ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")
        val secret = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        userMfaEntityService.savePending(userId, mfaSecretCipher.encrypt(userId, secret))
        val encoded = Base32.encode(secret)
        return MfaSetupDto(secret = encoded.chunked(4).joinToString(" "), otpauthUri = otpauthUri(loginId, encoded))
    }

    /**
     * 첫 코드 확인 — 맞으면 켜고, 현재 세션을 뺀 나머지 세션을 폐기한다(켜기 전에 만들어진, 2단계를 거치지 않은 세션).
     * 복구 코드를 새로 만들어 원본을 한 번만 돌려준다(남아 있던 코드는 지운다).
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun activate(userId: Long, code: String, currentSessionId: String): MfaRecoveryCodesDto {
        guardManageAttempts(userId)
        val credential = userMfaEntityService.lockCredential(userId) ?: throw MfaException(ErrorCode.MFA_SETUP_NOT_FOUND, "먼저 등록을 시작하세요")
        if (credential.status == MfaStatus.ACTIVE) throw MfaException(ErrorCode.MFA_ALREADY_ENABLED, "2차 인증이 이미 켜져 있습니다")
        if (!matchAndMarkTotp(userId, credential.totpSecretEncrypted, code)) failManage(userId)

        userMfaEntityService.activate(userId)
        val recoveryCodes = issueRecoveryCodes(userId)
        userSessionRevocationBizService.revokeAll(userId, MFA_ENABLED_REASON, exceptSessionId = currentSessionId)
        clearManageAttempts(userId)
        auditBizService.logSafely(eventType = AuditEventType.MFA_ENROLLED, eventCategory = AuditBizService.CATEGORY_AUTH, userId = userId, description = "2차 인증 등록")
        return MfaRecoveryCodesDto(recoveryCodes = recoveryCodes)
    }

    /** 복구 코드 재발급 — 현재 TOTP 로 재인증한다. 이전 코드는 모두 무효가 된다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun regenerateRecoveryCodes(userId: Long, totpCode: String): MfaRecoveryCodesDto {
        guardManageAttempts(userId)
        if (userMfaEntityService.lockCredential(userId)?.status != MfaStatus.ACTIVE) throw MfaException(ErrorCode.MFA_NOT_ENABLED, "2차 인증이 꺼져 있습니다")
        if (!verifyCode(userId, MfaMethod.TOTP, totpCode)) failManage(userId)
        val recoveryCodes = issueRecoveryCodes(userId)
        clearManageAttempts(userId)
        auditBizService.logSafely(
            eventType = AuditEventType.MFA_RECOVERY_CODES_REGENERATED,
            eventCategory = AuditBizService.CATEGORY_AUTH,
            userId = userId,
            description = "복구 코드 재발급",
        )
        return MfaRecoveryCodesDto(recoveryCodes = recoveryCodes)
    }

    /** 해제 — 현재 코드로 재인증한다. 등록과 복구 코드를 지운다. */
    @Transactional(transactionManager = "authTransactionManager")
    fun disable(userId: Long, method: MfaMethod, code: String) {
        guardManageAttempts(userId)
        if (userMfaEntityService.lockCredential(userId)?.status != MfaStatus.ACTIVE) throw MfaException(ErrorCode.MFA_NOT_ENABLED, "2차 인증이 꺼져 있습니다")
        if (!verifyCode(userId, method, code)) failManage(userId)
        userMfaEntityService.deleteAll(userId)
        clearManageAttempts(userId)
        auditBizService.logSafely(eventType = AuditEventType.MFA_DISABLED, eventCategory = AuditBizService.CATEGORY_AUTH, userId = userId, description = "2차 인증 해제")
    }

    /**
     * 켜져 있는(ACTIVE) 등록에 대해 코드를 확인한다 — 맞고 아직 쓰지 않은 구간이면 true. 실패 기록·감사는 호출자가 한다.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun verifyCode(userId: Long, method: MfaMethod, code: String): Boolean {
        val credential = userMfaEntityService.getCredential(userId)?.takeIf { it.status == MfaStatus.ACTIVE } ?: return false
        return when (method) {
            MfaMethod.TOTP -> matchAndMarkTotp(userId, credential.totpSecretEncrypted, code)
            MfaMethod.RECOVERY -> userMfaEntityService.markRecoveryCodeUsed(userId, tokenHasher.hash(normalizeRecoveryCode(code)))
        }
    }

    /** `XXXX-XXXX`(Base32 8자) 를 [MfaProperties.recoveryCodeCount] 개 만들어 해시로 저장하고 원본을 돌려준다. */
    private fun issueRecoveryCodes(userId: Long): List<String> {
        val codes = List(mfaProperties.recoveryCodeCount) { Base32.encode(ByteArray(RECOVERY_CODE_BYTES).also { random.nextBytes(it) }).chunked(4).joinToString("-") }
        userMfaEntityService.replaceRecoveryCodes(userId, codes.map { tokenHasher.hash(normalizeRecoveryCode(it)) })
        return codes
    }

    /** 사용자가 옮겨 적은 형식(소문자·하이픈·공백)을 저장 형식으로. */
    private fun normalizeRecoveryCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    private fun matchAndMarkTotp(userId: Long, encryptedSecret: String, code: String): Boolean {
        val step = Totp.matchStep(mfaSecretCipher.decrypt(userId, encryptedSecret), code, clock.instant()) ?: return false
        return userMfaEntityService.markTotpStepUsed(userId, step)
    }

    private fun otpauthUri(loginId: String, encodedSecret: String): String {
        val issuer = mfaProperties.issuer
        val label = encode("$issuer:$loginId")
        return "otpauth://totp/$label?secret=$encodedSecret&issuer=${encode(issuer)}&digits=${Totp.DIGITS}&period=${Totp.PERIOD_SECONDS}&algorithm=SHA1"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    // ===== 관리 API 시도 제한 — 로그인 잠금과 별개(이미 인증된 세션이 피해자 계정을 잠그지 못하게) =====

    private fun guardManageAttempts(userId: Long) {
        val attempts = redisTemplate.opsForValue().get(manageKey(userId))?.toIntOrNull() ?: 0
        if (attempts >= mfaProperties.manageMaxFailures) {
            throw MfaException(ErrorCode.MFA_TOO_MANY_ATTEMPTS, "코드를 너무 많이 틀렸습니다. 잠시 뒤 다시 시도하세요")
        }
    }

    private fun failManage(userId: Long): Nothing {
        redisTemplate.execute(INCREMENT_WITH_TTL, listOf(manageKey(userId)), mfaProperties.manageLockSeconds.toString())
        throw MfaException(ErrorCode.MFA_INVALID_CODE, "코드가 올바르지 않습니다")
    }

    private fun clearManageAttempts(userId: Long) {
        redisTemplate.delete(manageKey(userId))
    }

    private fun manageKey(userId: Long) = "$MANAGE_ATTEMPT_KEY_PREFIX$userId"

    companion object {
        const val MFA_ENABLED_REASON = "MFA_ENABLED"
        private const val SECRET_BYTES = 20 // RFC 4226 권장 160비트
        private const val RECOVERY_CODE_BYTES = 5 // Base32 8자 = 40비트
        private const val MANAGE_ATTEMPT_KEY_PREFIX = "mfa:manage:attempt:"

        /** 증가하고, 첫 값이면 만료를 건다 — 한 스크립트라 증가와 만료 사이에 키가 영구로 남지 않는다. */
        private val INCREMENT_WITH_TTL: RedisScript<Long> =
            RedisScript.of(
                """
                local value = redis.call('INCR', KEYS[1])
                if value == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
                return value
                """.trimIndent(),
                Long::class.java,
            )
    }
}