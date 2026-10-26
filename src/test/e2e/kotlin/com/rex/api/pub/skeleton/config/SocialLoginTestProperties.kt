package com.rex.api.pub.skeleton.config

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