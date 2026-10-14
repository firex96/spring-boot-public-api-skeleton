package com.rex.api.pub.skeleton.service.security

import com.rex.api.pub.skeleton.dto.security.BlockIpRequestDto
import com.rex.api.pub.skeleton.dto.security.BlockedIpDto
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.security.BlockedIpEntityService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 관리자 보안 표면(IP 차단·계정 잠금) 비즈니스 서비스 — `SecurityMappingService` 가 EntityService·`LoginAttemptBizService` 를
 * 직접 부르던 것을 이 계층으로 내렸다. 로그인 시도 카운터(Redis)는 `LoginAttemptBizService` 가 정본이고 여기서는 위임만 한다.
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class SecurityBizService(
    private val blockedIpEntityService: BlockedIpEntityService,
    private val loginAttemptBizService: LoginAttemptBizService,
) {
    // ===== IP 차단 =====

    fun getBlockedIps(pageable: Pageable): Page<BlockedIpDto> = blockedIpEntityService.getActiveBlockedIps(pageable)

    @Transactional(transactionManager = "authTransactionManager")
    fun blockIp(request: BlockIpRequestDto, blockedBy: Long): BlockedIpDto {
        val expiresAt = request.durationMinutes?.let { LocalDateTime.now().plusMinutes(it.toLong()) }
        return blockedIpEntityService.create(
            BlockedIpDto(
                ipAddress = request.ipAddress,
                blockReason = request.reason,
                description = request.description,
                expiresAt = expiresAt,
                blockedBy = blockedBy,
            ),
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun unblockIp(id: Long, unblockedBy: Long) {
        blockedIpEntityService.unblock(id, unblockedBy)
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun unblockIpByAddress(ipAddress: String, unblockedBy: Long) {
        blockedIpEntityService.unblockByIpAddress(ipAddress, unblockedBy)
    }

    fun isIpBlocked(ipAddress: String): Boolean = blockedIpEntityService.isBlocked(ipAddress)

    fun getActiveBlock(ipAddress: String): BlockedIpDto? = blockedIpEntityService.getActiveBlock(ipAddress)

    // ===== 계정 잠금 (Redis — 트랜잭션 무관) =====

    fun unlockAccount(username: String) {
        loginAttemptBizService.unlockUserAccount(username)
    }

    fun isUserLocked(username: String): Boolean = loginAttemptBizService.isUserLocked(username)

    fun getRemainingLockTime(username: String): Long = loginAttemptBizService.getRemainingLockTime(username)

    fun getCurrentAttempts(username: String): Int = loginAttemptBizService.getCurrentAttempts(username)
}