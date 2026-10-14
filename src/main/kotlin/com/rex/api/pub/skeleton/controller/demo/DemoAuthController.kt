package com.rex.api.pub.skeleton.controller.demo

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 인증 관련 데모 페이지 Controller
 */
@Controller
@RequestMapping("/demo/auth")
class DemoAuthController {
    @GetMapping("/login")
    fun login(): String = "demo/auth/login"

    @GetMapping("/social-login")
    fun socialLogin(): String = "demo/auth/social-login"

    @GetMapping("/signup")
    fun signup(): String = "demo/auth/signup"

    @GetMapping("/forgot-password")
    fun forgotPassword(): String = "demo/auth/forgot-password"

    @GetMapping("/reset-password")
    fun resetPassword(): String = "demo/auth/reset-password"

    @GetMapping("/verify-email")
    fun verifyEmail(): String = "demo/auth/verify-email"

    @GetMapping("/oauth2-consent")
    fun oauth2Consent(): String = "demo/auth/oauth2-consent"

    @GetMapping("/callback")
    fun callback(): String = "demo/auth/callback"
}