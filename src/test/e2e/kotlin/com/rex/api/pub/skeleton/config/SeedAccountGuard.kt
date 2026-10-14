package com.rex.api.pub.skeleton.config

import com.rex.api.pub.skeleton.service.security.LoginAttemptBizService
import com.rex.api.pub.skeleton.util.logger
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * 캐시된 Spring 컨텍스트당 1회 — V3 시드 존재 확인 + 시드 4계정의 잠금·실패 카운터 해제.
 *
 * 로그인 보호(5회 실패 → 30분 잠금)가 시드 관리자를 잠그면 관리자 e2e 전부가 `423 AUTH001` 로 무너진다.
 * 같은 실행 안에서 5회 실패하면 다시 잠기므로 이 가드는 「이전 실행의 잔재」만 푼다 — 그 경우는 `SeedAccounts.loginSeedAccount` 의 단서를 따른다.
 */
class SeedAccountGuard(
    private val e2eTestDataSetup: E2ETestDataSetup,
    private val loginAttemptBizService: LoginAttemptBizService,
) : ApplicationRunner {
    private val log = logger()

    override fun run(args: ApplicationArguments) {
        check(e2eTestDataSetup.verifyTestDataExists()) {
            "V3 시드 계정 누락 — skeleton_auth 에 V3__insert_e2e_test_data.sql 이 적용됐는지 확인 (local 프로파일 기동으로 Flyway 재적용)"
        }
        SEED_LOGIN_IDS.forEach { loginAttemptBizService.unlockUserAccount(it) }
        log.info("[e2e] 시드 계정 잠금·카운터 해제: ${SEED_LOGIN_IDS.size}")
    }

    companion object {
        val SEED_LOGIN_IDS = listOf(
            E2ETestDataSetup.TEST_USER_LOGIN_ID,
            E2ETestDataSetup.TEST_ADMIN_LOGIN_ID,
            E2ETestDataSetup.TEST_SUPER_ADMIN_LOGIN_ID,
            E2ETestDataSetup.SOCIAL_TEST_USER_LOGIN_ID,
        )
    }
}