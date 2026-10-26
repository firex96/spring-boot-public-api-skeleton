package com.rex.api.pub.skeleton.config

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