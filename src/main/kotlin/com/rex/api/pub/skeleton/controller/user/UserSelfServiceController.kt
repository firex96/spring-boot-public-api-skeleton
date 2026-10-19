package com.rex.api.pub.skeleton.controller.user

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.user.EmailVerificationConfirmDto
import com.rex.api.pub.skeleton.dto.user.EmailVerificationRequestDto
import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetConfirmDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetRequestDto
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.model.user.MessageResponseModel
import com.rex.api.pub.skeleton.model.user.ResultResponseModel
import com.rex.api.pub.skeleton.model.user.UserProfileModel
import com.rex.api.pub.skeleton.model.user.UserSessionModel
import com.rex.api.pub.skeleton.service.mapping.UserSelfMappingService
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User Self Service", description = "사용자 셀프 서비스 API")
@RestController
@RequestMapping("/user")
class UserSelfServiceController(
    private val mappingService: UserSelfMappingService,
    private val clientIpResolver: ClientIpResolver,
) {
    // ===== 프로필 =====

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/profile")
    fun getMyProfile(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<UserProfileModel> {
        val profile = mappingService.getProfile(user.userId)
        return ResponseEntity.ok(profile)
    }

    @Operation(summary = "내 프로필 수정")
    @PutMapping("/profile")
    fun updateMyProfile(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: UserProfileUpdateDto,
    ): ResponseEntity<UserProfileModel> {
        val profile = mappingService.updateProfile(user.userId, request)
        return ResponseEntity.ok(profile)
    }

    // ===== 비밀번호 관리 =====

    @Operation(summary = "비밀번호 재설정 요청", description = "이메일로 비밀번호 재설정 링크 발송")
    @PostMapping("/password/reset-request")
    fun requestPasswordReset(
        @Valid @RequestBody request: PasswordResetRequestDto,
    ): ResponseEntity<MessageResponseModel> {
        mappingService.requestPasswordReset(request.email)
        return ResponseEntity.ok(MessageResponseModel("이메일이 등록되어 있다면 비밀번호 재설정 링크가 발송됩니다"))
    }

    @Operation(summary = "비밀번호 재설정 완료", description = "토큰으로 비밀번호 재설정")
    @PostMapping("/password/reset")
    fun resetPassword(
        @Valid @RequestBody request: PasswordResetConfirmDto,
    ): ResponseEntity<ResultResponseModel> {
        val success = mappingService.resetPassword(request.token, request.newPassword)
        return if (success) {
            ResponseEntity.ok(ResultResponseModel(true, "비밀번호가 변경되었습니다"))
        } else {
            ResponseEntity.badRequest().body(ResultResponseModel(false, "유효하지 않거나 만료된 토큰입니다"))
        }
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 변경")
    @PostMapping("/password/change")
    fun changePassword(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: PasswordChangeDto,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ResultResponseModel> {
        val ipAddress = clientIpResolver.resolve(httpRequest)
        val success = mappingService.changePassword(user.userId, request, ipAddress, user.sessionId)
        return if (success) {
            ResponseEntity.ok(ResultResponseModel(true, "비밀번호가 변경되었습니다"))
        } else {
            ResponseEntity.badRequest().body(ResultResponseModel(false, "현재 비밀번호가 일치하지 않습니다"))
        }
    }

    // ===== 이메일 인증 =====

    @Operation(summary = "이메일 인증 요청", description = "이메일로 인증 링크 발송")
    @PostMapping("/email/verify-request")
    fun requestEmailVerification(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: EmailVerificationRequestDto,
    ): ResponseEntity<MessageResponseModel> {
        mappingService.requestEmailVerification(user.userId, request.email)
        return ResponseEntity.ok(MessageResponseModel("인증 이메일이 발송되었습니다"))
    }

    @Operation(summary = "이메일 인증 완료", description = "토큰으로 이메일 인증")
    @PostMapping("/email/verify")
    fun verifyEmail(
        @Valid @RequestBody request: EmailVerificationConfirmDto,
    ): ResponseEntity<ResultResponseModel> {
        val success = mappingService.verifyEmail(request.token)
        return if (success) {
            ResponseEntity.ok(ResultResponseModel(true, "이메일이 인증되었습니다"))
        } else {
            ResponseEntity.badRequest().body(ResultResponseModel(false, "유효하지 않거나 만료된 토큰입니다"))
        }
    }

    // ===== 세션 관리 =====

    @Operation(summary = "내 세션 목록 조회")
    @GetMapping("/sessions")
    fun getMySessions(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<List<UserSessionModel>> {
        val sessions = mappingService.getUserSessions(user.userId, user.sessionId)
        return ResponseEntity.ok(sessions)
    }

    @Operation(summary = "특정 세션 종료", description = "다른 기기에서 로그아웃")
    @DeleteMapping("/sessions/{sessionId}")
    fun revokeSession(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: Long,
    ): ResponseEntity<ResultResponseModel> {
        val success = mappingService.revokeSession(user.userId, sessionId, user.sessionId)
        return if (success) {
            ResponseEntity.ok(ResultResponseModel(true, "세션이 종료되었습니다"))
        } else {
            ResponseEntity.badRequest().body(ResultResponseModel(false, "세션을 종료할 수 없습니다"))
        }
    }

    @Operation(summary = "다른 모든 세션 종료", description = "현재 세션을 제외한 모든 기기에서 로그아웃")
    @DeleteMapping("/sessions")
    fun revokeAllOtherSessions(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<ResultResponseModel> {
        val count = mappingService.revokeAllOtherSessions(user.userId, user.sessionId)
        return ResponseEntity.ok(ResultResponseModel(true, "${count}개의 세션이 종료되었습니다"))
    }
}