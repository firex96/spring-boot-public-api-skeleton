package com.rex.api.pub.skeleton.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * E2E 테스트용 설정.
 *
 * application-e2e.yml의 e2e-test 설정을 읽어옵니다.
 * 환경변수로 오버라이드 가능합니다.
 */
@ConfigurationProperties(prefix = "e2e-test")
data class E2ETestProperties(
    val socialLogin: SocialLoginTestProperties = SocialLoginTestProperties(),
    val testUsers: TestUsersProperties = TestUsersProperties(),
) {
    // 편의 접근자 (하위 호환성)
    val kakao: SocialLoginTestProperties.KakaoTestAccount get() = socialLogin.kakao
    val naver: SocialLoginTestProperties.NaverTestAccount get() = socialLogin.naver
    val google: SocialLoginTestProperties.GoogleTestAccount get() = socialLogin.google
}

/**
 * 소셜 로그인 테스트 계정 설정.
 *
 * provider-id는 소셜 로그인 최초 실행 시 발견되며,
 * 이후 테스트를 위해 설정에 저장해야 합니다.
 */
data class SocialLoginTestProperties(
    val kakao: KakaoTestAccount = KakaoTestAccount(),
    val naver: NaverTestAccount = NaverTestAccount(),
    val google: GoogleTestAccount = GoogleTestAccount(),
) {
    data class KakaoTestAccount(
        val email: String? = null,
        val password: String? = null,
        /** 카카오에서 발급한 사용자 고유 ID (최초 로그인 시 발견) */
        val providerId: String? = null,
    ) {
        fun isConfigured(): Boolean = !email.isNullOrBlank() && !password.isNullOrBlank()
        fun isFullyConfigured(): Boolean = isConfigured() && !providerId.isNullOrBlank()
    }

    data class NaverTestAccount(
        val id: String? = null,
        val password: String? = null,
        /** 네이버에서 발급한 사용자 고유 ID (최초 로그인 시 발견) */
        val providerId: String? = null,
    ) {
        fun isConfigured(): Boolean = !id.isNullOrBlank() && !password.isNullOrBlank()
        fun isFullyConfigured(): Boolean = isConfigured() && !providerId.isNullOrBlank()
    }

    data class GoogleTestAccount(
        val email: String? = null,
        val password: String? = null,
        /** 구글에서 발급한 사용자 고유 ID (최초 로그인 시 발견) */
        val providerId: String? = null,
    ) {
        fun isConfigured(): Boolean = !email.isNullOrBlank() && !password.isNullOrBlank()
        fun isFullyConfigured(): Boolean = isConfigured() && !providerId.isNullOrBlank()
    }
}

/**
 * E2E 테스트용 사용자 계정 설정.
 */
data class TestUsersProperties(
    /** 일반 사용자 테스트 계정 */
    val user: TestUserAccount = TestUserAccount(),
    /** 관리자 테스트 계정 */
    val admin: TestUserAccount = TestUserAccount(),
    /** 최고 관리자 테스트 계정 */
    val superAdmin: TestUserAccount = TestUserAccount(),
) {
    data class TestUserAccount(
        val loginId: String? = null,
        val password: String? = null,
        val email: String? = null,
    ) {
        fun isConfigured(): Boolean = !loginId.isNullOrBlank() && !password.isNullOrBlank()
    }
}