package com.rex.api.pub.skeleton.controller.auth

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import com.rex.api.pub.skeleton.service.mapping.UserSelfMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 API. 세션 표면의 인가 규칙: **요청자(principal) 소유 세션만** 폐기한다 — 타인 세션은 403.
 */
@Tag(name = "Users", description = "사용자 API")
@RestController
@RequestMapping("/users")
class UserController(
    private val authMappingService: AuthMappingService,
    private val userSelfMappingService: UserSelfMappingService,
) {
    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<AuthUserInfoModel> {
        val userInfo = authMappingService.getCurrentUser(user.userId)
        return ResponseEntity.ok(userInfo)
    }

    @Operation(summary = "전체 세션 로그아웃 (현재 세션 제외)")
    @DeleteMapping("/me/sessions")
    fun logoutAllSessions(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Void> {
        authMappingService.logoutAllSessions(user.userId, excludeSessionId = user.sessionId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "특정 세션 로그아웃", description = "본인 세션만 — 타인 세션은 403, 없는 세션은 204")
    @DeleteMapping("/me/sessions/{sessionId}")
    fun logoutSession(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable sessionId: String,
    ): ResponseEntity<Void> {
        userSelfMappingService.revokeSessionBySessionId(user.userId, sessionId)
        return ResponseEntity.noContent().build()
    }
}