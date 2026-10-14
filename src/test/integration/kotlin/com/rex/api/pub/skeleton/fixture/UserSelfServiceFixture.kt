package com.rex.api.pub.skeleton.fixture

import com.rex.api.pub.skeleton.dto.user.EmailVerificationConfirmDto
import com.rex.api.pub.skeleton.dto.user.EmailVerificationRequestDto
import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetConfirmDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetRequestDto
import com.rex.api.pub.skeleton.dto.user.UserProfileDto
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.dto.user.UserSessionDto
import java.time.LocalDateTime
import java.util.UUID

/**
 * 사용자 셀프 서비스 관련 테스트 픽스처.
 *
 * 사용자 프로필, 비밀번호 관리, 이메일 인증, 세션 관리 API 테스트에서 사용됩니다.
 *
 * 사용 예시:
 * ```kotlin
 * val resetRequest = UserSelfServiceFixture.passwordResetRequest()
 * val profileUpdate = UserSelfServiceFixture.profileUpdateRequest()
 * ```
 */
object UserSelfServiceFixture {
    // 기본 사용자 정보
    const val DEFAULT_EMAIL = "test@example.com"
    const val DEFAULT_PASSWORD = "Test@1234!"
    const val DEFAULT_NAME = "테스트 사용자"
    const val DEFAULT_NICKNAME = "테스터"
    const val DEFAULT_PHONE = "010-1234-5678"
    const val DEFAULT_LOGIN_ID = "testuser"

    // ===== 비밀번호 재설정 =====

    /**
     * 비밀번호 재설정 요청 DTO.
     */
    fun passwordResetRequest(
        email: String = DEFAULT_EMAIL,
    ): PasswordResetRequestDto =
        PasswordResetRequestDto(
            email = email,
        )

    /**
     * 비밀번호 재설정 확인 DTO.
     */
    fun passwordResetConfirm(
        token: String = UUID.randomUUID().toString(),
        newPassword: String = "NewTest@1234!",
    ): PasswordResetConfirmDto =
        PasswordResetConfirmDto(
            token = token,
            newPassword = newPassword,
        )

    /**
     * 비밀번호 변경 DTO.
     */
    fun passwordChange(
        currentPassword: String = DEFAULT_PASSWORD,
        newPassword: String = "NewTest@1234!",
    ): PasswordChangeDto =
        PasswordChangeDto(
            currentPassword = currentPassword,
            newPassword = newPassword,
        )

    // ===== 이메일 인증 =====

    /**
     * 이메일 인증 요청 DTO.
     */
    fun emailVerificationRequest(
        email: String = DEFAULT_EMAIL,
    ): EmailVerificationRequestDto =
        EmailVerificationRequestDto(
            email = email,
        )

    /**
     * 이메일 인증 확인 DTO.
     */
    fun emailVerificationConfirm(
        token: String = UUID.randomUUID().toString(),
    ): EmailVerificationConfirmDto =
        EmailVerificationConfirmDto(
            token = token,
        )

    // ===== 세션 관리 =====

    /**
     * 사용자 세션 DTO.
     */
    fun userSessionDto(
        id: Long = 1L,
        sessionId: String = "****" + UUID.randomUUID().toString().takeLast(4),
        deviceType: String? = "WEB",
        deviceInfo: String? = "Chrome 120.0, Windows 10",
        ipAddress: String? = "127.0.0.1",
        lastActivityAt: LocalDateTime = LocalDateTime.now(),
        createdAt: LocalDateTime = LocalDateTime.now().minusHours(1),
        isCurrentSession: Boolean = false,
    ): UserSessionDto =
        UserSessionDto(
            id = id,
            sessionId = sessionId,
            deviceType = deviceType,
            deviceInfo = deviceInfo,
            ipAddress = ipAddress,
            lastActivityAt = lastActivityAt,
            createdAt = createdAt,
            isCurrentSession = isCurrentSession,
        )

    /**
     * 현재 세션 DTO.
     */
    fun currentSessionDto(
        id: Long = 1L,
        sessionId: String = "****" + UUID.randomUUID().toString().takeLast(4),
    ): UserSessionDto =
        userSessionDto(
            id = id,
            sessionId = sessionId,
            isCurrentSession = true,
        )

    /**
     * 세션 리스트 생성 (페이징 테스트용).
     */
    fun sessionDtoList(count: Int = 5): List<UserSessionDto> =
        (1..count).map { index ->
            userSessionDto(
                id = index.toLong(),
                deviceType = if (index % 2 == 0) "WEB" else "MOBILE",
                deviceInfo = "Device $index",
                ipAddress = "192.168.1.$index",
                isCurrentSession = index == 1,
                createdAt = LocalDateTime.now().minusHours(index.toLong()),
                lastActivityAt = LocalDateTime.now().minusMinutes(index.toLong() * 10),
            )
        }

    // ===== 프로필 =====

    /**
     * 사용자 프로필 DTO.
     */
    fun userProfileDto(
        id: Long = 1L,
        loginId: String = DEFAULT_LOGIN_ID,
        email: String? = DEFAULT_EMAIL,
        emailVerified: Boolean = true,
        phone: String? = DEFAULT_PHONE,
        name: String? = DEFAULT_NAME,
        nickname: String? = DEFAULT_NICKNAME,
        profileImage: String? = null,
        createdAt: LocalDateTime = LocalDateTime.now().minusDays(30),
    ): UserProfileDto =
        UserProfileDto(
            id = id,
            loginId = loginId,
            email = email,
            emailVerified = emailVerified,
            phone = phone,
            name = name,
            nickname = nickname,
            profileImage = profileImage,
            createdAt = createdAt,
        )

    /**
     * 이메일 미인증 사용자 프로필 DTO.
     */
    fun unverifiedEmailProfileDto(
        id: Long = 1L,
        email: String = "unverified@example.com",
    ): UserProfileDto =
        userProfileDto(
            id = id,
            email = email,
            emailVerified = false,
        )

    /**
     * 프로필 수정 요청 DTO.
     */
    fun profileUpdateRequest(
        name: String? = "수정된 이름",
        nickname: String? = "수정된 닉네임",
        phone: String? = "010-9876-5432",
        profileImage: String? = null,
    ): UserProfileUpdateDto =
        UserProfileUpdateDto(
            name = name,
            nickname = nickname,
            phone = phone,
            profileImage = profileImage,
        )

    /**
     * 잘못된 형식의 요청 모음 (Validation 테스트용).
     */
    object Invalid {
        /** 빈 이메일 비밀번호 재설정 요청 */
        fun passwordResetRequestWithEmptyEmail() =
            PasswordResetRequestDto(
                email = "",
            )

        /** 잘못된 이메일 형식 비밀번호 재설정 요청 */
        fun passwordResetRequestWithInvalidEmail() =
            PasswordResetRequestDto(
                email = "invalid-email",
            )

        /** 빈 토큰 비밀번호 재설정 확인 */
        fun passwordResetConfirmWithEmptyToken() =
            PasswordResetConfirmDto(
                token = "",
                newPassword = "NewTest@1234!",
            )

        /** 짧은 비밀번호 비밀번호 재설정 확인 */
        fun passwordResetConfirmWithShortPassword() =
            PasswordResetConfirmDto(
                token = UUID.randomUUID().toString(),
                newPassword = "1234567",
            )

        /** 빈 현재 비밀번호 비밀번호 변경 */
        fun passwordChangeWithEmptyCurrentPassword() =
            PasswordChangeDto(
                currentPassword = "",
                newPassword = "NewTest@1234!",
            )

        /** 짧은 새 비밀번호 비밀번호 변경 */
        fun passwordChangeWithShortNewPassword() =
            PasswordChangeDto(
                currentPassword = DEFAULT_PASSWORD,
                newPassword = "1234567",
            )

        /** 빈 이메일 이메일 인증 요청 */
        fun emailVerificationRequestWithEmptyEmail() =
            EmailVerificationRequestDto(
                email = "",
            )

        /** 잘못된 이메일 형식 이메일 인증 요청 */
        fun emailVerificationRequestWithInvalidEmail() =
            EmailVerificationRequestDto(
                email = "invalid-email",
            )

        /** 빈 토큰 이메일 인증 확인 */
        fun emailVerificationConfirmWithEmptyToken() =
            EmailVerificationConfirmDto(
                token = "",
            )

        /** 너무 긴 이름 프로필 수정 */
        fun profileUpdateWithTooLongName() =
            UserProfileUpdateDto(
                name = "a".repeat(101),
                nickname = null,
                phone = null,
                profileImage = null,
            )

        /** 너무 긴 프로필 이미지 URL 프로필 수정 */
        fun profileUpdateWithTooLongProfileImage() =
            UserProfileUpdateDto(
                name = null,
                nickname = null,
                phone = null,
                profileImage = "https://example.com/" + "a".repeat(500),
            )
    }
}