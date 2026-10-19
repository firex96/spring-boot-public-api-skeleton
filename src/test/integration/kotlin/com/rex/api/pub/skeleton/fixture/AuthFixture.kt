package com.rex.api.pub.skeleton.fixture

import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.RefreshTokenRequestDto
import com.rex.api.pub.skeleton.dto.auth.SessionInfo
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.dto.auth.UserInfoDto
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.RoleEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserEntity
import com.rex.api.pub.skeleton.persistence.jpa.entity.auth.UserGroupEntity
import java.time.Instant
import java.util.UUID

/**
 * 인증 관련 테스트 픽스처.
 *
 * 인증 API 테스트에서 사용되는 요청/응답 객체와 엔티티를 생성합니다.
 *
 * 사용 예시:
 * ```kotlin
 * val loginRequest = AuthFixture.loginRequest()
 * val signupRequest = AuthFixture.signupRequest(loginId = "newuser")
 * ```
 */
object AuthFixture {
    // 기본 테스트 사용자 정보
    const val DEFAULT_LOGIN_ID = "testuser"
    const val DEFAULT_PASSWORD = "Test@1234!"
    const val DEFAULT_EMAIL = "test@example.com"
    const val DEFAULT_NAME = "테스트 사용자"
    const val DEFAULT_NICKNAME = "테스터"
    const val DEFAULT_PHONE = "010-1234-5678"

    /**
     * 로그인 요청 DTO 생성.
     */
    fun loginRequest(
        loginId: String = DEFAULT_LOGIN_ID,
        password: String = DEFAULT_PASSWORD,
        deviceType: String? = "WEB",
        deviceInfo: String? = "Test Browser",
    ): LoginRequestDto =
        LoginRequestDto(
            loginId = loginId,
            password = password,
            deviceType = deviceType,
            deviceInfo = deviceInfo,
        )

    /**
     * 회원가입 요청 DTO 생성.
     */
    fun signupRequest(
        loginId: String = "newuser_${UUID.randomUUID().toString().take(8)}",
        password: String = DEFAULT_PASSWORD,
        email: String? = "new_${UUID.randomUUID().toString().take(8)}@example.com",
        name: String? = DEFAULT_NAME,
        nickname: String? = DEFAULT_NICKNAME,
        phone: String? = DEFAULT_PHONE,
    ): SignupRequestDto =
        SignupRequestDto(
            loginId = loginId,
            password = password,
            email = email,
            name = name,
            nickname = nickname,
            phone = phone,
        )

    /**
     * 비밀번호 변경 요청 DTO 생성.
     */
    fun changePasswordRequest(
        currentPassword: String = DEFAULT_PASSWORD,
        newPassword: String = "NewTest@1234!",
    ): ChangePasswordRequestDto =
        ChangePasswordRequestDto(
            currentPassword = currentPassword,
            newPassword = newPassword,
        )

    /**
     * 토큰 갱신 요청 DTO 생성.
     */
    fun refreshTokenRequest(
        refreshToken: String,
    ): RefreshTokenRequestDto =
        RefreshTokenRequestDto(
            refreshToken = refreshToken,
        )

    /**
     * 사용자 정보 DTO 생성.
     */
    fun userInfoDto(
        id: Long = 1L,
        loginId: String = DEFAULT_LOGIN_ID,
        email: String? = DEFAULT_EMAIL,
        name: String? = DEFAULT_NAME,
        nickname: String? = DEFAULT_NICKNAME,
        phone: String? = DEFAULT_PHONE,
        profileImage: String? = null,
        groups: List<String> = listOf("USER"),
    ): UserInfoDto =
        UserInfoDto(
            id = id,
            loginId = loginId,
            email = email,
            name = name,
            nickname = nickname,
            phone = phone,
            profileImage = profileImage,
            groups = groups,
        )

    fun authUserInfoModel(
        id: Long = 1L,
        loginId: String = DEFAULT_LOGIN_ID,
        email: String? = DEFAULT_EMAIL,
        name: String? = DEFAULT_NAME,
        nickname: String? = DEFAULT_NICKNAME,
        phone: String? = DEFAULT_PHONE,
        profileImage: String? = null,
        groups: List<String> = listOf("USER"),
    ): AuthUserInfoModel =
        AuthUserInfoModel(
            id = id,
            loginId = loginId,
            email = email,
            name = name,
            nickname = nickname,
            phone = phone,
            profileImage = profileImage,
            groups = groups,
        )

    /**
     * 사용자 엔티티 생성 (DB 저장용).
     *
     * @param passwordHash 이미 인코딩된 비밀번호 해시
     */
    fun userEntity(
        id: Long = 0L,
        loginId: String = DEFAULT_LOGIN_ID,
        passwordHash: String = "encoded_password_hash",
        email: String? = DEFAULT_EMAIL,
        name: String? = DEFAULT_NAME,
        nickname: String? = DEFAULT_NICKNAME,
        phone: String? = DEFAULT_PHONE,
        status: UserStatus = UserStatus.ACTIVE,
    ): UserEntity =
        UserEntity(
            loginId = loginId,
            passwordHash = passwordHash,
            email = email,
            name = name,
            nickname = nickname,
            phone = phone,
            status = status,
        ).also {
            if (id > 0) {
                // 리플렉션으로 id 설정 (테스트용)
                val idField = UserEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(it, id)
            }
        }

    /**
     * 역할(그룹) 엔티티 생성.
     */
    fun roleEntity(
        id: Long = 0,
        code: String = "USER",
        name: String = "일반 사용자",
        description: String? = "기본 사용자 그룹",
        isDefault: Boolean = true,
    ): RoleEntity =
        RoleEntity(
            code = code,
            name = name,
            description = description,
            isDefault = isDefault,
        ).also {
            if (id > 0) {
                val idField = RoleEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(it, id)
            }
        }

    /**
     * 사용자-그룹 매핑 엔티티 생성.
     */
    fun userGroupEntity(
        id: Long = 0L,
        userId: Long = 1L,
        groupId: Long = 1,
    ): UserGroupEntity =
        UserGroupEntity(
            userId = userId,
            groupId = groupId,
        ).also {
            if (id > 0) {
                val idField = UserGroupEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(it, id)
            }
        }

    /**
     * 세션 정보 생성 (테스트용).
     */
    fun createSessionInfo(
        sessionId: String = UUID.randomUUID().toString(),
        userId: Long = 1L,
        groups: List<String> = listOf("USER"),
        permissions: List<String> = emptyList(),
        deviceType: String? = "WEB",
        ipAddress: String? = "127.0.0.1",
        revoked: Boolean = false,
    ): SessionInfo =
        SessionInfo(
            userId = userId,
            sessionId = sessionId,
            groups = groups,
            permissions = permissions,
            deviceType = deviceType,
            ipAddress = ipAddress,
            issuedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(3600),
            authenticatedAt = Instant.now(),
            revoked = revoked,
        )

    /**
     * 잘못된 형식의 로그인 요청 모음 (Validation 테스트용).
     */
    object Invalid {
        /** 빈 로그인 ID */
        fun loginRequestWithEmptyLoginId() = LoginRequestDto(
            loginId = "",
            password = DEFAULT_PASSWORD,
        )

        /** 빈 비밀번호 */
        fun loginRequestWithEmptyPassword() = LoginRequestDto(
            loginId = DEFAULT_LOGIN_ID,
            password = "",
        )

        /** 짧은 로그인 ID (4자 미만) */
        fun signupRequestWithShortLoginId() = signupRequest(loginId = "abc")

        /** 짧은 비밀번호 (8자 미만) */
        fun signupRequestWithShortPassword() = signupRequest(password = "1234567")

        /** 잘못된 이메일 형식 */
        fun signupRequestWithInvalidEmail() = signupRequest(email = "invalid-email")

        /** 빈 현재 비밀번호 */
        fun changePasswordRequestWithEmptyCurrentPassword() = ChangePasswordRequestDto(
            currentPassword = "",
            newPassword = "NewTest@1234!",
        )

        /** 짧은 새 비밀번호 */
        fun changePasswordRequestWithShortNewPassword() = ChangePasswordRequestDto(
            currentPassword = DEFAULT_PASSWORD,
            newPassword = "1234567",
        )
    }
}