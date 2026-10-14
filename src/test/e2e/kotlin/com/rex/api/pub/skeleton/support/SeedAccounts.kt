package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * V3 시드 계정(`V3__insert_e2e_test_data.sql`)으로 로그인한다 — **실패는 실패다**(일반 사용자 폴백 없음).
 *
 * 폴백이 있으면 관리자 경로가 403 을 받아도 스위트가 통과해 관리자 e2e 가 공허해진다.
 */
fun TestRestTemplate.loginSeedAccount(loginId: String): String {
    val response =
        postJson<Map<String, Any>>(
            "/auth/login",
            LoginRequestDto(loginId = loginId, password = E2ETestDataSetup.DEFAULT_TEST_PASSWORD),
        )
    withClue(
        "시드 계정 로그인 실패: $loginId status=${response.statusCode} body=${response.body} — " +
            "423/AUTH001 이면 잠금: SeedAccountGuard 는 컨텍스트 기동 시 1회만 푼다. " +
            "같은 실행에서 5회 실패했다면 POST /admin/security/accounts/unlock/{loginId} 또는 30분 대기",
    ) { response.statusCode shouldBe HttpStatus.OK }
    return response.body.shouldNotBeNull()["accessToken"] as String
}

/**
 * 관리자 e2e 의 기본 토큰 — `e2e_test_super_admin`(모든 권한 + `SUPER_ADMIN` 역할).
 *
 * V2 의 `ADMIN` 역할은 `admin:security:manage`·`admin:permission:manage`·`oauth2:manage` 와
 * `hasRole('SUPER_ADMIN')` 엔드포인트에 닿지 못하므로 관리자 경로 검증에는 쓰지 않는다.
 */
fun TestRestTemplate.superAdminToken(): String = loginSeedAccount(E2ETestDataSetup.TEST_SUPER_ADMIN_LOGIN_ID)