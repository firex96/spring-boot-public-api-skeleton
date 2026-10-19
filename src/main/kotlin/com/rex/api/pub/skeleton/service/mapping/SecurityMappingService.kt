package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.config.security.SecurityProperties
import com.rex.api.pub.skeleton.dto.security.BlockIpRequestDto
import com.rex.api.pub.skeleton.mapper.SecurityMapper
import com.rex.api.pub.skeleton.model.security.BlockedIpModel
import com.rex.api.pub.skeleton.model.security.IpBlockStatusModel
import com.rex.api.pub.skeleton.model.security.LoginAttemptStatusModel
import com.rex.api.pub.skeleton.model.security.SecuritySettingsModel
import com.rex.api.pub.skeleton.service.mfa.MfaBizService
import com.rex.api.pub.skeleton.service.security.SecurityBizService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * 보안 매핑 서비스
 * DTO ↔ Model 변환 및 BizService 호출 담당
 */
@Service
class SecurityMappingService(
    private val securityBizService: SecurityBizService,
    private val mfaBizService: MfaBizService,
    private val securityProperties: SecurityProperties,
    private val mapper: SecurityMapper,
) {
    // ===== IP 차단 관리 =====

    fun getBlockedIps(pageable: Pageable): Page<BlockedIpModel> =
        securityBizService.getBlockedIps(pageable).map { mapper.toModel(it) }

    fun blockIp(request: BlockIpRequestDto, blockedBy: Long): BlockedIpModel =
        mapper.toModel(securityBizService.blockIp(request, blockedBy))

    fun unblockIp(id: Long, unblockedBy: Long) {
        securityBizService.unblockIp(id, unblockedBy)
    }

    fun unblockIpByAddress(ipAddress: String, unblockedBy: Long) {
        securityBizService.unblockIpByAddress(ipAddress, unblockedBy)
    }

    fun checkIpBlocked(ipAddress: String): IpBlockStatusModel {
        val isBlocked = securityBizService.isIpBlocked(ipAddress)
        val blockInfo = if (isBlocked) {
            securityBizService.getActiveBlock(ipAddress)?.let { mapper.toModel(it) }
        } else {
            null
        }

        return IpBlockStatusModel(
            ipAddress = ipAddress,
            isBlocked = isBlocked,
            blockInfo = blockInfo,
        )
    }

    // ===== 계정 잠금 관리 =====

    fun unlockAccount(username: String) {
        securityBizService.unlockAccount(username)
    }

    fun resetMfa(userId: Long, adminId: Long) {
        mfaBizService.resetByAdmin(userId, adminId)
    }

    fun getAccountStatus(username: String): LoginAttemptStatusModel {
        val isLocked = securityBizService.isUserLocked(username)
        val remainingTime = if (isLocked) {
            securityBizService.getRemainingLockTime(username)
        } else {
            null
        }

        return LoginAttemptStatusModel(
            username = username,
            currentAttempts = securityBizService.getCurrentAttempts(username),
            maxAttempts = securityProperties.maxLoginAttempts,
            isLocked = isLocked,
            remainingLockTimeSeconds = remainingTime,
        )
    }

    // ===== 보안 설정 =====

    fun getSecuritySettings(): SecuritySettingsModel = SecuritySettingsModel(
        enableBruteForceProtection = securityProperties.enableBruteForceProtection,
        maxLoginAttempts = securityProperties.maxLoginAttempts,
        loginAttemptWindowMinutes = securityProperties.loginAttemptWindowMinutes,
        accountLockDurationMinutes = securityProperties.accountLockDurationMinutes,
        enableIpBlocking = securityProperties.enableIpBlocking,
        maxIpLoginAttempts = securityProperties.maxIpLoginAttempts,
        ipBlockDurationMinutes = securityProperties.ipBlockDurationMinutes,
        permanentBlockThreshold = securityProperties.permanentBlockThreshold,
    )
}