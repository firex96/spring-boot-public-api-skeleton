package com.rex.api.pub.skeleton.controller.auth

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.auth.SocialLoginRequestDto
import com.rex.api.pub.skeleton.enums.auth.SocialProvider
import com.rex.api.pub.skeleton.model.auth.SocialAccountModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.mapping.SocialAuthMappingService
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Tag(name = "Social Auth", description = "소셜 로그인 API")
@RestController
@RequestMapping("/auth/oauth2")
class SocialAuthController(
    private val socialAuthMappingService: SocialAuthMappingService,
    private val clientIpResolver: ClientIpResolver,
) {
    @Operation(summary = "소셜 로그인 URL 조회", description = "소셜 로그인을 위한 인증 URL을 반환합니다")
    @GetMapping("/{provider}/authorize")
    fun getAuthorizationUrl(
        @PathVariable provider: SocialProvider,
    ): ResponseEntity<Map<String, String>> {
        val state = UUID.randomUUID().toString()
        val authorizationUrl = socialAuthMappingService.getAuthorizationUrl(provider, state)
        return ResponseEntity.ok(
            mapOf(
                "authorizationUrl" to authorizationUrl,
                "state" to state,
            ),
        )
    }

    @Operation(summary = "소셜 로그인 콜백 (리다이렉트)", description = "OAuth 인증 후 콜백 처리 - 프론트엔드로 리다이렉트")
    @GetMapping("/{provider}/callback")
    fun handleCallback(
        @PathVariable provider: SocialProvider,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(name = "error_description", required = false) errorDescription: String?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        // 에러 처리
        if (error != null) {
            // 데모 콜백 페이지로 에러 리다이렉트
            // 값 하나에 인코딩 한 번 — provider 가 준 error·error_description 의 &·#·공백이 쿼리 구분자로 새지 않게
            val encodedError = URLEncoder.encode(error, StandardCharsets.UTF_8)
            val encodedDescription = URLEncoder.encode(errorDescription ?: "", StandardCharsets.UTF_8)
            httpResponse.sendRedirect("/demo/auth/callback?error=$encodedError&error_description=$encodedDescription")
            return
        }

        // code가 없는 경우 에러 처리
        if (code.isNullOrBlank()) {
            httpResponse.sendRedirect("/demo/auth/callback?error=missing_code&error_description=Authorization%20code%20is%20missing")
            return
        }

        // 로그인 처리
        val ipAddress = clientIpResolver.resolve(httpRequest)
        val tokens =
            socialAuthMappingService.socialLogin(
                provider = provider,
                code = code,
                deviceType = "WEB",
                deviceInfo = httpRequest.getHeader("User-Agent"),
                ipAddress = ipAddress,
            )

        // 데모 콜백 페이지로 토큰과 함께 리다이렉트
        // 실제 운영에서는 보안을 위해 짧은 수명의 코드를 발급하고 프론트엔드에서 교환하는 방식 권장
        httpResponse.sendRedirect(
            "/demo/auth/callback?access_token=${tokens.accessToken}&refresh_token=${tokens.refreshToken}&expires_in=${tokens.expiresIn}",
        )
    }

    @Operation(summary = "소셜 로그인 (코드 교환)", description = "Authorization code를 토큰으로 교환합니다")
    @PostMapping("/{provider}/login")
    fun socialLogin(
        @PathVariable provider: SocialProvider,
        @Valid @RequestBody request: SocialLoginRequestDto,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<TokenResponseModel> {
        val ipAddress = clientIpResolver.resolve(httpRequest)
        val tokens =
            socialAuthMappingService.socialLogin(
                provider = provider,
                code = request.code,
                deviceType = request.deviceType,
                deviceInfo = request.deviceInfo,
                ipAddress = ipAddress,
            )
        return ResponseEntity.ok(tokens)
    }

    @Operation(summary = "소셜 계정 연동", description = "기존 계정에 소셜 계정을 연동합니다")
    @PostMapping("/{provider}/link")
    fun linkSocialAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable provider: SocialProvider,
        @Valid @RequestBody request: SocialLoginRequestDto,
    ): ResponseEntity<SocialAccountModel> {
        val socialAccount =
            socialAuthMappingService.linkSocialAccount(
                userId = user.userId,
                provider = provider,
                code = request.code,
                ipAddress = null,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(socialAccount)
    }

    @Operation(summary = "소셜 계정 연동 해제", description = "연동된 소셜 계정을 해제합니다")
    @DeleteMapping("/{provider}/unlink")
    fun unlinkSocialAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable provider: SocialProvider,
    ): ResponseEntity<Void> {
        socialAuthMappingService.unlinkSocialAccount(user.userId, provider, null)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "연동된 소셜 계정 목록 조회")
    @GetMapping("/accounts")
    fun getSocialAccounts(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<SocialAccountModel>> {
        val accounts = socialAuthMappingService.getSocialAccounts(user.userId)
        return ResponseEntity.ok(accounts)
    }
}