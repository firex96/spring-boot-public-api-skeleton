package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 2차 인증 설정 등록 — `SecurityConfiguration` 은 `!test` 프로필에서만 뜨므로 거기 두지 않는다(MFA 빈은 프로필과 무관하게 필요하다).
 */
@Configuration
@EnableConfigurationProperties(MfaProperties::class)
class MfaConfiguration