package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserRepository
import com.rex.api.pub.skeleton.util.logger

/**
 * E2E 테스트 데이터 유틸리티.
 *
 * 테스트 데이터는 Flyway 마이그레이션(V3__insert_e2e_test_data.sql)으로 관리됩니다.
 * 이 클래스는 테스트에서 사용할 유틸리티 메서드만 제공합니다.
 *
 * ## 테스트 데이터 구성
 *
 * Flyway 마이그레이션이 다음 데이터를 자동 생성합니다:
 * - e2e_test_user: 일반 사용자 (USER 역할)
 * - e2e_test_admin: 관리자 (ADMIN 역할)
 * - e2e_test_super_admin: 최고 관리자 (SUPER_ADMIN 역할)
 * - e2e_test_social: 소셜 로그인 테스트용 사용자 (KAKAO, NAVER 연동)
 *
 * ## 소셜 로그인 테스트 설정
 *
 * provider-id는 Flyway 마이그레이션에 하드코딩되어 있습니다.
 * 새로운 provider-id가 필요한 경우:
 *
 * 1. SocialAccountQueryTest 또는 discover-provider-ids 태그 테스트 실행
 * 2. 발견된 provider-id를 e2e 시드 마이그레이션(V3)과 application-e2e.yml에 추가
 *
 * @see com.rex.api.pub.skeleton.auth.SocialAccountQueryTest
 */
class E2ETestDataSetup(
    private val userRepository: UserRepository,
    private val testProperties: E2ETestProperties,
) {
    private val log = logger()

    companion object {
        // 테스트용 사용자 로그인 ID 접두사
        const val TEST_USER_PREFIX = "e2e_test_"

        // 테스트용 사용자 정보 (Flyway 마이그레이션과 일치해야 함)
        const val TEST_USER_LOGIN_ID = "${TEST_USER_PREFIX}user"
        const val TEST_ADMIN_LOGIN_ID = "${TEST_USER_PREFIX}admin"
        const val TEST_SUPER_ADMIN_LOGIN_ID = "${TEST_USER_PREFIX}super_admin"
        const val DEFAULT_TEST_PASSWORD = "Test@1234!"

        // 소셜 로그인 테스트용 사용자
        const val SOCIAL_TEST_USER_LOGIN_ID = "${TEST_USER_PREFIX}social"
    }

    /**
     * 테스트 사용자 ID 조회.
     *
     * @param roleCode 역할 코드 (USER, ADMIN, SUPER_ADMIN)
     * @return 사용자 ID 또는 null
     */
    fun getTestUserId(roleCode: String): Long? {
        val loginId = when (roleCode) {
            "USER" -> testProperties.testUsers.user.loginId ?: TEST_USER_LOGIN_ID
            "ADMIN" -> testProperties.testUsers.admin.loginId ?: TEST_ADMIN_LOGIN_ID
            "SUPER_ADMIN" -> testProperties.testUsers.superAdmin.loginId ?: TEST_SUPER_ADMIN_LOGIN_ID
            else -> null
        }
        return loginId?.let { userRepository.findByLoginId(it).orElse(null)?.id }
    }

    /**
     * 소셜 로그인 테스트 사용자 ID 조회.
     *
     * @return 소셜 테스트 사용자 ID 또는 null
     */
    fun getSocialTestUserId(): Long? =
        userRepository.findByLoginId(SOCIAL_TEST_USER_LOGIN_ID).orElse(null)?.id

    /**
     * 테스트 데이터 존재 여부 확인.
     *
     * Flyway 마이그레이션이 정상 적용되었는지 확인합니다.
     *
     * @return 테스트 데이터가 존재하면 true
     */
    fun verifyTestDataExists(): Boolean {
        val requiredUsers = listOf(
            TEST_USER_LOGIN_ID,
            TEST_ADMIN_LOGIN_ID,
            TEST_SUPER_ADMIN_LOGIN_ID,
            SOCIAL_TEST_USER_LOGIN_ID,
        )

        val missingUsers = requiredUsers.filter { loginId ->
            userRepository.findByLoginId(loginId).isEmpty
        }

        if (missingUsers.isNotEmpty()) {
            log.warn("E2E 테스트 데이터 누락: $missingUsers")
            log.warn("Flyway 마이그레이션(V3__insert_e2e_test_data.sql)이 적용되었는지 확인하세요.")
            return false
        }

        log.debug("E2E 테스트 데이터 확인 완료")
        return true
    }
}