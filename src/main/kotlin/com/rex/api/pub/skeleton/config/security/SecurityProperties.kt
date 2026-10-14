package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security.login-protection")
data class SecurityProperties(
    // 로그인 시도 제한
    val maxLoginAttempts: Int = 5, // 최대 로그인 시도 횟수
    val loginAttemptWindowMinutes: Int = 15, // 시도 횟수 계산 시간 창 (분)
    val accountLockDurationMinutes: Int = 30, // 계정 잠금 시간 (분)

    // IP 차단
    val maxIpLoginAttempts: Int = 10, // IP당 최대 로그인 시도 횟수
    val ipBlockDurationMinutes: Int = 60, // IP 차단 시간 (분)
    val permanentBlockThreshold: Int = 50, // 영구 차단 임계값

    // 보안 이벤트 감지
    val suspiciousActivityThreshold: Int = 20, // 의심스러운 활동 임계값
    val enableBruteForceProtection: Boolean = true,
    val enableIpBlocking: Boolean = true,

    // 프록시 설정 (X-Forwarded-For 신뢰)
    val trustedProxies: List<String> = emptyList(), // 신뢰할 프록시 IP (CIDR 지원: 10.0.0.0/8)
    val trustAllProxies: Boolean = false, // true면 모든 X-Forwarded-For 신뢰 (개발용)
)