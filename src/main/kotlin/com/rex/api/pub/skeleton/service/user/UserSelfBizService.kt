package com.rex.api.pub.skeleton.service.user

import com.rex.api.pub.skeleton.dto.auth.EmailVerificationTokenDto
import com.rex.api.pub.skeleton.dto.auth.PasswordResetTokenDto
import com.rex.api.pub.skeleton.dto.notification.MailMessage
import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.dto.user.UserProfileDto
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.dto.user.UserSessionDto
import com.rex.api.pub.skeleton.enums.auth.AuditEventType
import com.rex.api.pub.skeleton.exception.auth.ForbiddenException
import com.rex.api.pub.skeleton.exception.auth.ResourceNotFoundException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.TokenEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSessionEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.auth.UserSessionRevocationBizService
import com.rex.api.pub.skeleton.service.notification.MailSender
import com.rex.api.pub.skeleton.util.TokenHasher
import com.rex.api.pub.skeleton.util.logger
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 사용자 셀프 서비스 (비즈니스 서비스)
 * 비밀번호 재설정, 이메일 인증, 세션 관리, 프로필 관리 비즈니스 로직 담당
 */
@Service
@Transactional(readOnly = true, transactionManager = "authTransactionManager")
class UserSelfBizService(
    private val userEntityService: UserEntityService,
    private val userSessionEntityService: UserSessionEntityService,
    private val tokenEntityService: TokenEntityService,
    private val sessionCacheBizService: SessionCacheBizService,
    private val passwordEncoder: PasswordEncoder,
    private val auditBizService: AuditBizService,
    private val tokenHasher: TokenHasher,
    private val mailSender: MailSender,
    private val userSessionRevocationBizService: UserSessionRevocationBizService,
) {
    private val log = logger()

    companion object {
        private const val PASSWORD_RESET_TOKEN_VALIDITY_HOURS = 24L
        private const val EMAIL_VERIFICATION_TOKEN_VALIDITY_HOURS = 48L
        const val PASSWORD_RESET_MAIL_SUBJECT = "[Public API Skeleton] 비밀번호 재설정"
        const val EMAIL_VERIFICATION_MAIL_SUBJECT = "[Public API Skeleton] 이메일 인증"
        const val PASSWORD_CHANGED_REASON = "PASSWORD_CHANGED"
    }

    // ===== 비밀번호 재설정 =====

    @Transactional(transactionManager = "authTransactionManager")
    fun requestPasswordReset(email: String) {
        val user = userEntityService.getUserByEmail(email)
            ?: return // 보안상 이메일 존재 여부 노출하지 않음

        // 기존 토큰 무효화
        tokenEntityService.invalidateAllPasswordResetTokensByUserId(user.id)

        // 새 토큰 생성 (원본 + 해시)
        val tokenPair = tokenHasher.generateToken()
        tokenEntityService.createPasswordResetToken(
            userId = user.id,
            tokenHash = tokenPair.hashedToken, // 해시만 저장
            expiresAt = LocalDateTime.now().plusHours(PASSWORD_RESET_TOKEN_VALIDITY_HOURS),
        )
        log.info("비밀번호 재설정 토큰 생성: userId=${user.id}")

        // 원본 토큰은 메일로만 나간다 — 발송 실패는 전파되어 토큰 저장이 롤백된다
        mailSender.send(
            MailMessage(
                to = email,
                subject = PASSWORD_RESET_MAIL_SUBJECT,
                body = "비밀번호 재설정 토큰: ${tokenPair.rawToken} (${PASSWORD_RESET_TOKEN_VALIDITY_HOURS}시간 유효). 데모: /demo/auth/reset-password?token=${tokenPair.rawToken}",
            ),
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun resetPassword(token: String, newPassword: String): Boolean {
        // 입력 토큰을 해시하여 DB에서 검색
        val tokenHash = tokenHasher.hash(token)
        val resetToken = tokenEntityService.getUnusedPasswordResetToken(tokenHash)
            ?: return false

        if (!resetToken.isValid()) {
            return false
        }

        val user = userEntityService.getUserById(resetToken.userId)
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")

        // 비밀번호 변경
        userEntityService.updatePassword(user.id, encodePassword(newPassword))

        // 토큰 사용 처리
        tokenEntityService.markPasswordResetTokenUsed(resetToken.id)

        // 모든 세션 무효화
        invalidateAllUserSessions(user.id)

        auditBizService.logSafely(
            eventType = AuditEventType.PASSWORD_RESET_COMPLETE,
            eventCategory = AuditBizService.CATEGORY_AUTH,
            userId = user.id,
            username = user.loginId,
            description = "비밀번호 재설정 완료",
        )

        log.info("비밀번호 재설정 완료: userId=${user.id}")
        return true
    }

    /**
     * 비밀번호 변경 — 성공하면 **현재 세션을 뺀** 나머지 세션을 폐기한다. 탈취된 다른 세션은 끊기고, 방금 바꾼 기기는 계속 쓴다.
     *
     * 폐기 규칙은 `UserSessionRevocationBizService` 가 정본이다.
     * `/auth/password/change` 는 현재 세션까지 모두 폐기한다 — 두 경로의 차이는 README 「인증과 인가」.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun changePassword(userId: Long, request: PasswordChangeDto, ipAddress: String?, currentSessionId: String): Boolean {
        val user = userEntityService.getUserById(userId)
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            return false
        }

        // 비밀번호 변경
        userEntityService.updatePassword(userId, encodePassword(request.newPassword))

        userSessionRevocationBizService.revokeAll(userId, PASSWORD_CHANGED_REASON, exceptSessionId = currentSessionId)

        auditBizService.logPasswordChange(userId, user.loginId, ipAddress)

        log.info("비밀번호 변경 완료: userId=$userId")
        return true
    }

    // ===== 이메일 인증 =====

    @Transactional(transactionManager = "authTransactionManager")
    fun requestEmailVerification(userId: Long, email: String) {
        userEntityService.getUserById(userId)
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")

        // 기존 토큰 무효화
        tokenEntityService.invalidateAllEmailVerificationTokensByUserId(userId)

        // 새 토큰 생성 (원본 + 해시)
        val tokenPair = tokenHasher.generateToken()
        tokenEntityService.createEmailVerificationToken(
            userId = userId,
            email = email,
            tokenHash = tokenPair.hashedToken, // 해시만 저장
            expiresAt = LocalDateTime.now().plusHours(EMAIL_VERIFICATION_TOKEN_VALIDITY_HOURS),
        )
        log.info("이메일 인증 토큰 생성: userId=$userId, email=$email")

        // 원본 토큰은 메일로만 나간다 — 발송 실패는 전파되어 토큰 저장이 롤백된다
        mailSender.send(
            MailMessage(
                to = email,
                subject = EMAIL_VERIFICATION_MAIL_SUBJECT,
                body = "이메일 인증 토큰: ${tokenPair.rawToken} (${EMAIL_VERIFICATION_TOKEN_VALIDITY_HOURS}시간 유효). 데모: /demo/auth/verify-email?token=${tokenPair.rawToken}",
            ),
        )
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun verifyEmail(token: String): Boolean {
        // 입력 토큰을 해시하여 DB에서 검색
        val tokenHash = tokenHasher.hash(token)
        val verificationToken = tokenEntityService.getUnverifiedEmailVerificationToken(tokenHash)
            ?: return false

        if (!verificationToken.isValid()) {
            return false
        }

        val user = userEntityService.getUserById(verificationToken.userId)
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")

        // 이메일 업데이트
        userEntityService.updateEmail(user.id, verificationToken.email)

        // 토큰 인증 완료 처리
        tokenEntityService.markEmailVerificationTokenVerified(verificationToken.id)

        auditBizService.logSafely(
            eventType = AuditEventType.EMAIL_VERIFIED,
            eventCategory = AuditBizService.CATEGORY_ACCOUNT,
            userId = user.id,
            username = user.loginId,
            description = "이메일 인증 완료: ${verificationToken.email}",
        )

        log.info("이메일 인증 완료: userId=${user.id}, email=${verificationToken.email}")
        return true
    }

    // ===== 세션 관리 =====

    fun getUserSessions(userId: Long, currentSessionId: String?): List<UserSessionDto> =
        userSessionEntityService.getActiveSessionDtosByUserId(userId, currentSessionId)

    @Transactional(transactionManager = "authTransactionManager")
    fun revokeSession(userId: Long, sessionId: Long, currentSessionId: String): Boolean {
        val session = userSessionEntityService.getById(sessionId)
            ?: return false

        if (session.userId != userId) {
            return false // 다른 사용자의 세션 취소 불가
        }

        if (session.sessionId == currentSessionId) {
            return false // 현재 세션 취소 불가 (로그아웃 사용)
        }

        // 세션 무효화
        userSessionEntityService.revoke(session.sessionId, "사용자가 다른 기기에서 로그아웃")

        // 캐시 무효화
        sessionCacheBizService.invalidateSession(session.sessionId)

        log.info("세션 취소: userId=$userId, sessionId=$sessionId")
        return true
    }

    /**
     * sessionId(JWT 의 세션 식별자)로 세션을 폐기한다 — `DELETE /users/me/sessions/{sessionId}`.
     * 요청자 소유가 아니면 [ForbiddenException](403). 없거나 이미 폐기된 세션은 멱등(무동작). 현재 세션도 허용한다.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun revokeSessionBySessionId(userId: Long, sessionId: String) {
        val session = userSessionEntityService.getUnrevokedBySessionId(sessionId)
        if (session == null) {
            log.info("세션 폐기 생략(없음 또는 이미 폐기): userId=$userId, sessionId=$sessionId")
            return
        }
        if (session.userId != userId) {
            log.warn("타 사용자 세션 종료 시도 차단: requester=$userId, owner=${session.userId}, sessionId=$sessionId")
            throw ForbiddenException("본인의 세션만 종료할 수 있습니다")
        }
        userSessionEntityService.revoke(sessionId, "LOGOUT")
        sessionCacheBizService.invalidateSession(sessionId)
        log.info("세션 폐기: userId=$userId, sessionId=$sessionId")
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun revokeAllOtherSessions(userId: Long, currentSessionId: String): Int {
        val sessions = userSessionEntityService.getUnrevokedByUserId(userId)
            .filter { it.sessionId != currentSessionId }

        sessions.forEach { session ->
            userSessionEntityService.revoke(session.sessionId, "사용자가 모든 다른 기기에서 로그아웃")
            sessionCacheBizService.invalidateSession(session.sessionId)
        }

        log.info("모든 다른 세션 취소: userId=$userId, count=${sessions.size}")
        return sessions.size
    }

    // ===== 프로필 관리 =====

    fun getProfile(userId: Long): UserProfileDto {
        val user = userEntityService.getUserById(userId) ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")
        return userEntityService.getUserProfileById(userId, tokenEntityService.existsVerifiedEmail(userId, user.email))
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")
    }

    @Transactional(transactionManager = "authTransactionManager")
    fun updateProfile(userId: Long, request: UserProfileUpdateDto): UserProfileDto {
        userEntityService.getUserById(userId)
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다")

        userEntityService.updateProfile(
            userId = userId,
            name = request.name,
            nickname = request.nickname,
            phone = request.phone,
            profileImage = request.profileImage,
        )

        return getProfile(userId)
    }

    // ===== Private Methods =====

    private fun invalidateAllUserSessions(userId: Long) {
        val sessions = userSessionEntityService.getUnrevokedByUserId(userId)
        sessions.forEach { session ->
            userSessionEntityService.revoke(session.sessionId, "비밀번호 변경으로 인한 세션 무효화")
            sessionCacheBizService.invalidateSession(session.sessionId)
        }
    }

    private fun encodePassword(raw: String): String =
        passwordEncoder.encode(raw) ?: throw IllegalStateException("PasswordEncoder 가 null 을 반환했다")

    /** Entity 의 `isValid()` 와 같은 판정 — 미사용·미만료. */
    private fun PasswordResetTokenDto.isValid(): Boolean = !isUsed && LocalDateTime.now().isBefore(expiresAt)

    private fun EmailVerificationTokenDto.isValid(): Boolean = !isVerified && LocalDateTime.now().isBefore(expiresAt)
}