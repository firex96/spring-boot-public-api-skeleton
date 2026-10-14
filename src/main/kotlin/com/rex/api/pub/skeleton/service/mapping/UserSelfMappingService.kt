package com.rex.api.pub.skeleton.service.mapping

import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.mapper.AuthMapper
import com.rex.api.pub.skeleton.model.user.UserProfileModel
import com.rex.api.pub.skeleton.model.user.UserSessionModel
import com.rex.api.pub.skeleton.service.user.UserSelfBizService
import org.springframework.stereotype.Service

/**
 * 사용자 셀프 서비스 매핑 서비스
 * DTO ↔ Model 변환 및 BizService 호출 담당
 */
@Service
class UserSelfMappingService(
    private val userSelfBizService: UserSelfBizService,
    private val mapper: AuthMapper,
) {
    // ===== 프로필 =====

    fun getProfile(userId: Long): UserProfileModel? =
        userSelfBizService.getProfile(userId)?.let { mapper.toUserProfileModel(it) }

    fun updateProfile(userId: Long, request: UserProfileUpdateDto): UserProfileModel? =
        userSelfBizService.updateProfile(userId, request)?.let { mapper.toUserProfileModel(it) }

    // ===== 비밀번호 관리 =====

    fun requestPasswordReset(email: String) {
        userSelfBizService.requestPasswordReset(email)
    }

    fun resetPassword(token: String, newPassword: String): Boolean =
        userSelfBizService.resetPassword(token, newPassword)

    fun changePassword(userId: Long, request: PasswordChangeDto, ipAddress: String): Boolean =
        userSelfBizService.changePassword(userId, request, ipAddress)

    // ===== 이메일 인증 =====

    fun requestEmailVerification(userId: Long, email: String) {
        userSelfBizService.requestEmailVerification(userId, email)
    }

    fun verifyEmail(token: String): Boolean =
        userSelfBizService.verifyEmail(token)

    // ===== 세션 관리 =====

    fun getUserSessions(userId: Long, currentSessionId: String?): List<UserSessionModel> =
        userSelfBizService.getUserSessions(userId, currentSessionId).map { mapper.toUserSessionModel(it) }

    fun revokeSession(userId: Long, sessionId: Long, currentSessionId: String?): Boolean {
        if (currentSessionId == null) return false
        return userSelfBizService.revokeSession(userId, sessionId, currentSessionId)
    }

    fun revokeSessionBySessionId(userId: Long, sessionId: String) = userSelfBizService.revokeSessionBySessionId(userId, sessionId)

    fun revokeAllOtherSessions(userId: Long, currentSessionId: String?): Int {
        if (currentSessionId == null) return 0
        return userSelfBizService.revokeAllOtherSessions(userId, currentSessionId)
    }
}