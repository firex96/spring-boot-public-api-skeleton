package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.service.auth.SessionCacheBizService
import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * E2E 테스트용 설정.
 *
 * docker compose 로 띄운 로컬 인프라(MySQL·Redis)를 사용하여 E2E 테스트를 실행합니다.
 * 설정은 src/test/e2e/resources/application-e2e.yml에서 관리합니다.
 *
 * 실행 전 요구사항:
 * - docker compose up -d (MySQL·Redis — local 프로필 값)
 *
 * 사용 예시:
 * ```kotlin
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * @Import(E2ETestConfiguration::class)
 * class MyE2ETest {
 *     // ...
 * }
 * ```
 *
 * ## 테스트 데이터
 *
 * 테스트 데이터는 Flyway 마이그레이션(V3__insert_e2e_test_data.sql)으로 관리됩니다:
 * - 테스트용 사용자 (e2e_test_user, e2e_test_admin, e2e_test_super_admin)
 * - 소셜 로그인 연동 데이터 (e2e_test_social)
 *
 * ## 소셜 로그인 테스트 설정
 *
 * 새로운 provider-id 발견이 필요한 경우:
 * ```bash
 * ./gradlew e2eTest --tests "*.SocialAccountQueryTest"
 * ```
 *
 * 발견된 provider-id를 e2e 시드 마이그레이션(V3)과 application-e2e.yml에 추가
 */
@TestConfiguration
@EnableConfigurationProperties(E2ETestProperties::class)
class E2ETestConfiguration {
    /**
     * E2E 테스트 데이터 유틸리티 빈.
     *
     * 테스트 데이터는 Flyway 마이그레이션(V3__insert_e2e_test_data.sql)으로 관리됩니다.
     */
    @Bean
    fun e2eTestDataSetup(
        userRepository: com.rex.api.pub.skeleton.persistence.jpa.repository.auth.UserRepository,
        testProperties: E2ETestProperties,
    ): E2ETestDataSetup = E2ETestDataSetup(userRepository, testProperties)

    /** 컨텍스트당 1회 — 시드 존재 확인 + 시드 4계정 잠금 해제(`SeedAccountGuard`). */
    @Bean
    fun seedAccountGuard(
        e2eTestDataSetup: E2ETestDataSetup,
        loginAttemptBizService: LoginAttemptBizService,
    ): SeedAccountGuard = SeedAccountGuard(e2eTestDataSetup, loginAttemptBizService)

    /** spec 이 만든 auth 행을 SQL 로 지우는 정리기 — 각 spec 의 `afterSpec { janitor.cleanup(resources) }`. */
    @Bean
    fun e2eResourceJanitor(
        @Qualifier("authJdbcTemplate") authJdbcTemplate: JdbcTemplate,
        @Qualifier("authTransactionManager") authTransactionManager: PlatformTransactionManager,
        sessionCacheBizService: SessionCacheBizService,
        loginAttemptBizService: LoginAttemptBizService,
    ): E2eResourceJanitor = E2eResourceJanitor(authJdbcTemplate, authTransactionManager, sessionCacheBizService, loginAttemptBizService)

    /**
     * 테스트용 인메모리 캐시 매니저.
     *
     * Redis 대신 ConcurrentMapCache를 사용하여
     * 테스트 간 캐시 격리를 보장합니다.
     */
    @Bean
    @Primary
    fun testCacheManager(): CacheManager = ConcurrentMapCacheManager()
}