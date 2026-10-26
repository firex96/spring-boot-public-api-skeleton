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