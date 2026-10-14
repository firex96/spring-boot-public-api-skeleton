package com.rex.api.pub.skeleton.controller.auth

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.dto.auth.ChangePasswordRequestDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.RefreshTokenRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.model.auth.AccessTokenResponseModel
import com.rex.api.pub.skeleton.model.auth.AuthUserInfoModel
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
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
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authMappingService: AuthMappingService,
    private val clientIpResolver: ClientIpResolver,
) {
    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequestDto,
    ): ResponseEntity<AuthUserInfoModel> {
        val user = authMappingService.signup(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequestDto,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<TokenResponseModel> {
        val ipAddress = clientIpResolver.resolve(httpRequest)
        val tokens = authMappingService.login(request, ipAddress)
        return ResponseEntity.ok(tokens)
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Void> {
        authMappingService.logout(user.sessionId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequestDto,
    ): ResponseEntity<AccessTokenResponseModel> {
        val token = authMappingService.refresh(request.refreshToken)
        return ResponseEntity.ok(token)
    }

    @Operation(summary = "비밀번호 변경")
    @PostMapping("/password/change")
    fun changePassword(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: ChangePasswordRequestDto,
    ): ResponseEntity<Void> {
        authMappingService.changePassword(user.userId, request)
        return ResponseEntity.noContent().build()
    }
}