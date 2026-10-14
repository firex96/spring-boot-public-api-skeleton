package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpStatus
import java.util.UUID

/** e2e 가 만든 사용자 — 토큰은 이 사용자로 로그인한 세션의 것. */
data class E2eUser(val loginId: String, val userId: Long, val accessToken: String)

/** `prefix` 뒤에 8자 난수 — 정리 쿼리(Q-1)가 `e2e\_%` 로 잡을 수 있게 prefix 는 `e2e_` 로 시작한다. */
fun uniqueE2eLoginId(prefix: String): String = "$prefix${UUID.randomUUID().toString().take(8)}"

/**
 * 회원가입만 한다 — `201` 을 단언하고 `resources` 에 등록한다. 반환은 (loginId, userId).
 */
fun TestRestTemplate.signupUser(
    resources: E2eResources,
    prefix: String,
    password: String = E2ETestDataSetup.DEFAULT_TEST_PASSWORD,
    email: String? = null,
    name: String? = null,
    loginId: String = uniqueE2eLoginId(prefix),
): Pair<String, Long> {
    val response = postJson<Map<String, Any>>(
        "/auth/signup",
        SignupRequestDto(loginId = loginId, password = password, email = email ?: "$loginId@example.com", name = name),
    )
    resources.user(loginId) // 201 이 아니어도 행이 생겼을 수 있다 — 단언 전에 등록
    withClue("signup 실패: $loginId status=${response.statusCode} body=${response.body}") { response.statusCode shouldBe HttpStatus.CREATED }
    val userId = (response.body.shouldNotBeNull()["id"] as Number).toLong()
    return loginId to userId
}

/**
 * 회원가입 `201` · 로그인 `200` 을 단언하고 등록한 사용자를 돌려준다 — `!!` 없음, 실패는 실패다.
 */
fun TestRestTemplate.createAndLoginUser(
    resources: E2eResources,
    prefix: String,
    password: String = E2ETestDataSetup.DEFAULT_TEST_PASSWORD,
    email: String? = null,
    name: String? = null,
    loginId: String = uniqueE2eLoginId(prefix),
): E2eUser {
    val (_, userId) = signupUser(resources, prefix, password, email, name, loginId)
    val login = postJson<Map<String, Any>>("/auth/login", LoginRequestDto(loginId = loginId, password = password))
    withClue("login 실패: $loginId status=${login.statusCode} body=${login.body}") { login.statusCode shouldBe HttpStatus.OK }
    return E2eUser(loginId, userId, login.body.shouldNotBeNull()["accessToken"] as String)
}