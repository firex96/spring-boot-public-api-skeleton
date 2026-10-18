package com.rex.api.pub.skeleton.controller.auth

import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.exception.auth.InvalidTokenException
import com.rex.api.pub.skeleton.model.auth.TokenResponseModel
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 브라우저 세션 API — 브라우저 체인(세션 쿠키 + CSRF 헤더)을 탄다. */
@Tag(name = "Browser Session", description = "브라우저 세션의 토큰 교환")
@RestController
@RequestMapping("/auth/session")
class BrowserSessionController(
    private val authMappingService: AuthMappingService,
) {
    @Operation(
        summary = "소셜 콜백 토큰 교환",
        description = "소셜 콜백이 맡겨 둔 토큰을 한 번만 돌려준다. 세션 쿠키와 CSRF 헤더가 필요하다. 없거나 이미 꺼냈으면 401",
    )
    @PostMapping("/token")
    fun takeToken(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<TokenResponseModel> {
        val tokens = authMappingService.takeTokenHandoff(user.sessionId) ?: throw InvalidTokenException("교환할 토큰이 없습니다")
        return ResponseEntity.ok(tokens)
    }
}