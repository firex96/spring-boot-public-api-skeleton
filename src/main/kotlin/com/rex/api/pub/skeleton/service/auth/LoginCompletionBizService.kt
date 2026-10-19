package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.dto.auth.IssuedSessionDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증 완료 지점 — 인증이 **최종적으로** 끝난 사용자에게 세션을 발급하고 성공을 기록한다. 2차 인증이 없는 1단계 성공과 2단계 성공이 모두 여기로 온다
 * (`MfaChallengeBizService.afterFirstFactor`·`complete`).
 *
 * 실패 카운터는 [clearAttempts] 일 때만 지운다 — 비밀번호 1단계·2단계 성공은 지우고, 소셜 1단계 성공은 지우지 않는다(소셜 검증은 비밀번호 추측과 무관하다).
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class LoginCompletionBizService(
    private val sessionIssueBizService: SessionIssueBizService,
    private val loginAttemptBizService: LoginAttemptBizService,
    private val auditBizService: AuditBizService,
) {
    @Transactional(transactionManager = "authTransactionManager")
    fun complete(
        user: UserDto,
        provider: SocialProvider?,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
        clearAttempts: Boolean,
    ): IssuedSessionDto {
        if (clearAttempts) loginAttemptBizService.clearAttempts(user.loginId, ipAddress)
        val issued = sessionIssueBizService.issue(user, deviceType, deviceInfo, ipAddress)
        if (provider != null) {
            auditBizService.logSocialLoginSuccess(user.id, user.loginId, provider.name, ipAddress, deviceInfo, issued.sessionId)
        } else {
            auditBizService.logLoginSuccess(user.id, user.loginId, ipAddress, deviceInfo, issued.sessionId)
        }
        return issued
    }
}