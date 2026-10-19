package com.rex.api.pub.skeleton.service.auth

import com.rex.api.pub.skeleton.client.social.SocialOAuth2Client
import com.rex.api.pub.skeleton.dto.auth.OAuth2TokenResponse
import com.rex.api.pub.skeleton.dto.auth.SocialAccountDto
import com.rex.api.pub.skeleton.dto.auth.SocialUserInfo
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserDto
import com.rex.api.pub.skeleton.dto.auth.UserSocialAccountDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.enums.auth.UserStatus
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserEntityService
import com.rex.api.pub.skeleton.persistence.jpa.service.auth.UserSocialAccountEntityService
import com.rex.api.pub.skeleton.service.audit.AuditBizService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 소셜 로그인 서비스.
 *
 * 소셜 로그인은 기존 계정에 연동된 경우에만 허용됩니다.
 * - 소셜 로그인으로 신규 계정 생성 불가
 * - 이메일 기반 자동 연동 불가
 * - 반드시 먼저 계정 생성 후 소셜 계정 연동 필요
 */
@Service
class SocialAuthBizService(
    private val socialOAuth2Clients: List<SocialOAuth2Client>,
    private val userEntityService: UserEntityService,
    private val userSocialAccountEntityService: UserSocialAccountEntityService,
    private val sessionIssueBizService: SessionIssueBizService,
    private val auditBizService: AuditBizService,
) {
    private val log = logger()

    private fun getOAuth2Client(provider: SocialProvider): SocialOAuth2Client =
        socialOAuth2Clients.find { it.provider == provider }
            ?: throw AuthenticationException("지원하지 않는 소셜 로그인입니다: $provider")

    /**
     * 소셜 로그인 인증 URL 조회 — [state] 는 `SocialAuthorizationStateBizService.issue` 가 발급한 값이다.
     */
    fun getAuthorizationUrl(
        provider: SocialProvider,
        state: String,
    ): String = getOAuth2Client(provider).getAuthorizationUrl(state)

    /**
     * 소셜 로그인 — 연동된 계정만 통과한다. 실패 감사는 `noRollbackFor` 로 커밋된다(`AuthBizService.login` 과 같은 이유).
     */
    @Transactional(transactionManager = "authTransactionManager", noRollbackFor = [AuthenticationException::class])
    fun socialLogin(
        provider: SocialProvider,
        code: String,
        state: String?,
        deviceType: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): TokenResponseDto {
        val oauth2Client = getOAuth2Client(provider)

        val tokenResponse = try {
            oauth2Client.getAccessToken(code, state)
        } catch (e: Exception) {
            log.error("소셜 토큰 획득 실패: provider=$provider, error=${e.message}")
            auditBizService.logSocialLoginFailure(
                provider = provider.name,
                providerId = null,
                ipAddress = ipAddress,
                userAgent = deviceInfo,
                reason = "토큰 획득 실패: ${e.message}",
            )
            throw AuthenticationException("소셜 인증에 실패했습니다: ${e.message}")
        }

        val socialUserInfo = try {
            oauth2Client.getUserInfo(tokenResponse.accessToken)
        } catch (e: Exception) {
            log.error("소셜 사용자 정보 조회 실패: provider=$provider, error=${e.message}")
            auditBizService.logSocialLoginFailure(
                provider = provider.name,
                providerId = null,
                ipAddress = ipAddress,
                userAgent = deviceInfo,
                reason = "사용자 정보 조회 실패: ${e.message}",
            )
            throw AuthenticationException("소셜 사용자 정보를 가져올 수 없습니다")
        }

        val socialAccount = userSocialAccountEntityService.getByProviderAndProviderId(provider.name, socialUserInfo.providerId)
        if (socialAccount == null) {
            auditBizService.logSocialLoginFailure(
                provider = provider.name,
                providerId = socialUserInfo.providerId,
                ipAddress = ipAddress,
                userAgent = deviceInfo,
                reason = "연동되지 않은 소셜 계정",
            )
            throw AuthenticationException("연동되지 않은 소셜 계정입니다. 먼저 계정에 소셜 로그인을 연동해주세요.")
        }

        val user = userEntityService.getUserById(socialAccount.userId)
        if (user == null) {
            auditBizService.logSocialLoginFailure(
                provider = provider.name,
                providerId = socialUserInfo.providerId,
                ipAddress = ipAddress,
                userAgent = deviceInfo,
                reason = "사용자를 찾을 수 없음",
            )
            throw AuthenticationException("사용자를 찾을 수 없습니다")
        }
        if (user.status != UserStatus.ACTIVE) {
            auditBizService.logSocialLoginFailure(
                provider = provider.name,
                providerId = socialUserInfo.providerId,
                ipAddress = ipAddress,
                userAgent = deviceInfo,
                reason = "비활성 사용자: ${user.status}",
            )
            throw AuthenticationException("비활성화된 계정입니다. 관리자에게 문의하세요.")
        }

        updateSocialAccountInfo(socialAccount, socialUserInfo, tokenResponse)
        syncUserProfile(user, socialUserInfo)

        val issued = sessionIssueBizService.issue(user, deviceType, deviceInfo, ipAddress)

        auditBizService.logSocialLoginSuccess(
            userId = user.id,
            username = user.loginId,
            provider = provider.name,
            ipAddress = ipAddress,
            userAgent = deviceInfo,
            sessionId = issued.sessionId,
        )
        return issued.tokens
    }

    /**
     * 소셜 계정 연동 — 이미 다른 사용자가 연동한 provider 계정, 같은 provider 중복 연동은 거절한다.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun linkSocialAccount(
        userId: Long,
        provider: SocialProvider,
        code: String,
        ipAddress: String? = null,
    ): SocialAccountDto {
        val oauth2Client = getOAuth2Client(provider)
        val user = userEntityService.getUserById(userId) ?: throw AuthenticationException("사용자를 찾을 수 없습니다")

        val tokenResponse = oauth2Client.getAccessToken(code, null)
        val socialUserInfo = oauth2Client.getUserInfo(tokenResponse.accessToken)

        val existingAccount = userSocialAccountEntityService.getByProviderAndProviderId(provider.name, socialUserInfo.providerId)
        if (existingAccount != null) {
            if (existingAccount.userId == userId) {
                throw AuthenticationException("이미 연동된 계정입니다")
            } else {
                throw AuthenticationException("다른 사용자가 이미 연동한 계정입니다")
            }
        }
        if (userSocialAccountEntityService.getByUserIdAndProvider(userId, provider.name) != null) {
            throw AuthenticationException("이미 ${provider.name} 계정이 연동되어 있습니다. 먼저 기존 연동을 해제해주세요.")
        }

        val linked =
            userSocialAccountEntityService.link(
                userId = userId,
                provider = provider.name,
                providerId = socialUserInfo.providerId,
                email = socialUserInfo.email,
                name = socialUserInfo.name,
                profileImage = socialUserInfo.profileImage,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                tokenExpiresAt = tokenResponse.expiresIn?.let { LocalDateTime.now().plusSeconds(it) },
            )

        auditBizService.logSocialAccountLinked(
            userId = userId,
            username = user.loginId,
            provider = provider.name,
            providerId = socialUserInfo.providerId,
            ipAddress = ipAddress,
        )

        return SocialAccountDto(
            provider = provider,
            providerId = socialUserInfo.providerId,
            email = socialUserInfo.email,
            name = socialUserInfo.name,
            connectedAt = linked.createdAt,
        )
    }

    /**
     * 소셜 계정 연동 해제 — 마지막 로그인 수단(비밀번호 없음·다른 소셜 없음)은 해제할 수 없다.
     */
    @Transactional(transactionManager = "authTransactionManager")
    fun unlinkSocialAccount(
        userId: Long,
        provider: SocialProvider,
        ipAddress: String? = null,
    ) {
        val account =
            userSocialAccountEntityService.getByUserIdAndProvider(userId, provider.name)
                ?: throw AuthenticationException("연동된 소셜 계정이 없습니다")
        val user = userEntityService.getUserById(userId) ?: throw AuthenticationException("사용자를 찾을 수 없습니다")

        val hasPassword = !user.passwordHash.isNullOrBlank()
        val hasOtherSocialAccount = userSocialAccountEntityService.getAllByUserId(userId).size > 1
        if (!hasPassword && !hasOtherSocialAccount) {
            throw AuthenticationException("마지막 로그인 수단은 해제할 수 없습니다. 비밀번호를 먼저 설정해주세요.")
        }

        userSocialAccountEntityService.unlink(account.id)

        auditBizService.logSocialAccountUnlinked(
            userId = userId,
            username = user.loginId,
            provider = provider.name,
            ipAddress = ipAddress,
        )
    }

    fun getSocialAccounts(userId: Long): List<SocialAccountDto> =
        userSocialAccountEntityService.getAllByUserId(userId).map { account ->
            SocialAccountDto(
                provider = SocialProvider.valueOf(account.provider),
                providerId = account.providerId,
                email = account.email,
                name = account.name,
                connectedAt = account.createdAt,
            )
        }

    private fun updateSocialAccountInfo(
        socialAccount: UserSocialAccountDto,
        socialUserInfo: SocialUserInfo,
        tokenResponse: OAuth2TokenResponse,
    ) {
        userSocialAccountEntityService.updateTokensAndProfile(
            id = socialAccount.id,
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            tokenExpiresAt = tokenResponse.expiresIn?.let { LocalDateTime.now().plusSeconds(it) },
            email = socialUserInfo.email,
            name = socialUserInfo.name,
            profileImage = socialUserInfo.profileImage,
        )
    }

    /** 비어 있는 프로필 필드만 provider 값으로 채운다 — 채울 것이 없으면 쓰지 않는다. */
    private fun syncUserProfile(
        user: UserDto,
        socialUserInfo: SocialUserInfo,
    ) {
        val name = socialUserInfo.name?.takeIf { it.isNotBlank() && user.name.isNullOrBlank() }
        val profileImage = socialUserInfo.profileImage?.takeIf { it.isNotBlank() && user.profileImage.isNullOrBlank() }
        val nickname = socialUserInfo.name?.takeIf { it.isNotBlank() && user.nickname.isNullOrBlank() }
        if (name != null || profileImage != null || nickname != null) {
            userEntityService.updateProfile(userId = user.id, name = name, nickname = nickname, phone = null, profileImage = profileImage)
        }
    }
}