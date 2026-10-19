package com.rex.api.pub.skeleton.controller.user

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.auth.MfaCodeRequestDto
import com.rex.api.pub.skeleton.dto.auth.MfaVerifyRequestDto
import com.rex.api.pub.skeleton.model.auth.MfaRecoveryCodesModel
import com.rex.api.pub.skeleton.model.auth.MfaSetupModel
import com.rex.api.pub.skeleton.model.auth.MfaStatusModel
import com.rex.api.pub.skeleton.service.mapping.UserMfaMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 2차 인증(TOTP) 등록·관리 — 본인만. 켜기·끄기의 재인증은 비밀번호가 아니라 현재 코드다(소셜 전용 사용자는 비밀번호가 없다).
 */
@Tag(name = "User MFA", description = "2차 인증 등록·관리 API")
@RestController
@RequestMapping("/user/mfa")
class UserMfaController(
    private val userMfaMappingService: UserMfaMappingService,
) {
    @Operation(summary = "2차 인증 상태")
    @GetMapping
    fun status(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<MfaStatusModel> = ResponseEntity.ok(userMfaMappingService.status(user.userId))

    @Operation(summary = "TOTP 등록 시작", description = "새 시크릿과 otpauth URI — 첫 코드를 activate 로 확인해야 켜진다. 이미 켜져 있으면 409")
    @PostMapping("/totp/setup")
    fun setup(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<MfaSetupModel> = ResponseEntity.ok(userMfaMappingService.setup(user.userId))

    @Operation(summary = "TOTP 활성화", description = "첫 코드가 맞으면 켜고 다른 세션을 모두 폐기한다. 복구 코드 10개(원본은 이 응답에서만)")
    @PostMapping("/totp/activate")
    fun activate(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: MfaCodeRequestDto,
    ): ResponseEntity<MfaRecoveryCodesModel> = ResponseEntity.ok(userMfaMappingService.activate(user.userId, request, user.sessionId))

    @Operation(summary = "복구 코드 재발급", description = "현재 TOTP 로 재인증한다. 새 코드 10개(원본은 이 응답에서만) — 이전 코드는 모두 무효")
    @PostMapping("/recovery-codes")
    fun regenerateRecoveryCodes(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: MfaCodeRequestDto,
    ): ResponseEntity<MfaRecoveryCodesModel> = ResponseEntity.ok(userMfaMappingService.regenerateRecoveryCodes(user.userId, request))

    @Operation(summary = "2차 인증 해제", description = "현재 코드(TOTP 또는 복구 코드)로 재인증한다")
    @PostMapping("/disable")
    fun disable(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: MfaVerifyRequestDto,
    ): ResponseEntity<Void> {
        userMfaMappingService.disable(user.userId, request)
        return ResponseEntity.noContent().build()
    }
}