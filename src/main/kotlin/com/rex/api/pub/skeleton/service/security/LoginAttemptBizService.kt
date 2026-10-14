package com.rex.api.pub.skeleton.service.security

import com.rex.api.pub.skeleton.config.security.SecurityProperties
import com.rex.api.pub.skeleton.dto.security.BlockedIpDto
import com.rex.api.pub.skeleton.enums.auth.BlockReason
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.security.BlockedIpEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class LoginAttemptBizService(
    private val redisTemplate: StringRedisTemplate,
    private val blockedIpEntityService: BlockedIpEntityService,
    private val auditBizService: AuditBizService,
    private val securityProperties: SecurityProperties,
) {
    private val log = logger()

    companion object {
        private const val USER_ATTEMPT_KEY_PREFIX = "login:attempt:user:"
        private const val IP_ATTEMPT_KEY_PREFIX = "login:attempt:ip:"
        private const val USER_LOCKED_KEY_PREFIX = "login:locked:user:"
    }

    /**
     * 로그인 시도 기록 (실패 시).
     *
     * `ipAddress == null`(클라이언트 IP 를 신뢰할 수 없어 알 수 없음)이면 IP 카운터·IP 차단은 건너뛰고 사용자 카운터·잠금만 기록한다 —
     * 공유 버킷에 모으면 임계에서 그 값이 `blocked_ips` 에 저장돼 IP 없는 정상 로그인이 전부 403 이 된다.
     */
    fun recordFailedAttempt(username: String, ipAddress: String?) {
        if (!securityProperties.enableBruteForceProtection) return

        // 사용자별 실패 횟수 증가
        val userAttempts = incrementAttempts(
            "$USER_ATTEMPT_KEY_PREFIX$username",
            securityProperties.loginAttemptWindowMinutes,
        )

        // IP별 실패 횟수 증가 — IP 를 알 때만
        val ipAttempts = ipAddress?.let {
            incrementAttempts("$IP_ATTEMPT_KEY_PREFIX$it", securityProperties.loginAttemptWindowMinutes)
        }

        log.debug("로그인 실패 기록: user=$username, ip=$ipAddress, userAttempts=$userAttempts, ipAttempts=$ipAttempts")

        // 사용자 계정 잠금 확인
        if (userAttempts >= securityProperties.maxLoginAttempts) {
            lockUserAccount(username)
        }

        // IP 차단 확인
        if (ipAddress != null && ipAttempts != null && securityProperties.enableIpBlocking && ipAttempts >= securityProperties.maxIpLoginAttempts) {
            blockIp(ipAddress, ipAttempts)
        }
    }

    /**
     * 로그인 성공 시 시도 횟수 초기화
     */
    fun clearAttempts(username: String, ipAddress: String?) {
        redisTemplate.delete("$USER_ATTEMPT_KEY_PREFIX$username")
        // IP 시도 횟수는 유지 (다른 사용자 시도도 포함되므로)
    }

    /**
     * 사용자 계정 잠금 여부 확인
     */
    fun isUserLocked(username: String): Boolean {
        // 보호를 끈 프로파일에서는 남아 있는 잠금 키가 로그인을 막지 않는다 — isIpBlocked 와 같은 계약
        if (!securityProperties.enableBruteForceProtection) return false

        val key = "$USER_LOCKED_KEY_PREFIX$username"
        return redisTemplate.hasKey(key) ?: false
    }

    /**
     * IP 차단 여부 확인
     */
    fun isIpBlocked(ipAddress: String): Boolean {
        if (!securityProperties.enableIpBlocking) return false

        return blockedIpEntityService.isBlocked(ipAddress)
    }

    /**
     * 계정 잠금 해제
     */
    fun unlockUserAccount(username: String) {
        val key = "$USER_LOCKED_KEY_PREFIX$username"
        redisTemplate.delete(key)
        redisTemplate.delete("$USER_ATTEMPT_KEY_PREFIX$username")
        log.info("계정 잠금 해제: $username")
    }

    /**
     * IP 차단 해제
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun unblockIp(ipAddress: String, unblockedBy: Long?) {
        blockedIpEntityService.unblockByIpAddress(ipAddress, unblockedBy)
        log.info("IP 차단 해제: $ipAddress")
    }

    /**
     * 남은 잠금 시간 (초)
     */
    fun getRemainingLockTime(username: String): Long {
        val key = "$USER_LOCKED_KEY_PREFIX$username"
        return redisTemplate.getExpire(key, TimeUnit.SECONDS)
    }

    /**
     * 현재 로그인 시도 횟수 조회
     */
    fun getCurrentAttempts(username: String): Int {
        val key = "$USER_ATTEMPT_KEY_PREFIX$username"
        return redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
    }

    // ===== Private Methods =====

    private fun incrementAttempts(key: String, windowMinutes: Int): Long {
        val attempts = redisTemplate.opsForValue().increment(key) ?: 1
        if (attempts == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes.toLong()))
        }
        return attempts
    }

    private fun lockUserAccount(username: String) {
        val key = "$USER_LOCKED_KEY_PREFIX$username"
        redisTemplate.opsForValue().set(
            key,
            "locked",
            Duration.ofMinutes(securityProperties.accountLockDurationMinutes.toLong()),
        )

        auditBizService.logAccountLocked(
            userId = null,
            username = username,
            ipAddress = null,
            reason = "로그인 ${securityProperties.maxLoginAttempts}회 실패",
        )

        log.warn("계정 잠금: $username (${securityProperties.accountLockDurationMinutes}분)")
    }

    /** `recordFailedAttempt` → 여기까지 호출자(`AuthBizService.login`)의 auth 트랜잭션 안에서 실행된다 — private 메서드의 `@Transactional` 은 프록시가 가로채지 못해 무효였다. */
    private fun blockIp(ipAddress: String, attempts: Long) {
        // 이미 차단된 경우 무시
        if (isIpBlocked(ipAddress)) return

        val isPermanent = attempts >= securityProperties.permanentBlockThreshold
        val expiresAt = if (isPermanent) {
            null
        } else {
            LocalDateTime.now().plusMinutes(securityProperties.ipBlockDurationMinutes.toLong())
        }

        blockedIpEntityService.create(
            BlockedIpDto(
                ipAddress = ipAddress,
                blockReason = BlockReason.BRUTE_FORCE,
                description = "로그인 실패 $attempts 회",
                expiresAt = expiresAt,
            ),
        )

        auditBizService.logIpBlocked(ipAddress, "Brute Force 감지: $attempts 회 시도")
        auditBizService.logBruteForceDetected(null, ipAddress)

        if (isPermanent) {
            log.error("IP 영구 차단: $ipAddress (시도 $attempts 회)")
        } else {
            log.warn("IP 임시 차단: $ipAddress (${securityProperties.ipBlockDurationMinutes}분)")
        }
    }
}