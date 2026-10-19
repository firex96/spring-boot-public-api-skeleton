package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 2차 인증(TOTP) 설정. [secretEncryptionKey] 는 비밀이다 — 논리 키 `mfa-secret-encryption-key`(base64 32바이트), 기본값 없음.
 */
@ConfigurationProperties(prefix = "mfa")
data class MfaProperties(
    val secretEncryptionKey: String,
    val issuer: String = "Skeleton", // otpauth URI 의 issuer — 인증 앱에 표시되는 이름
    val challengeTtlSeconds: Long = 300, // 1단계 통과 뒤 2단계를 마칠 때까지
    val maxChallengeFailures: Int = 5, // challenge 하나에서 틀릴 수 있는 횟수 — 넘으면 challenge 를 지운다
    val manageMaxFailures: Int = 5, // 등록·관리 API 의 코드 확인 실패 한도
    val manageLockSeconds: Long = 900, // 관리 API 실패 한도에 닿은 뒤 막는 시간
)