package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.AccessTokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.RefreshTokenRequestDto
import com.rex.api.pub.skeleton.dto.auth.SignupRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.auth.UserInfoDto
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Auth API E2E 테스트.
 *
 * 실제 HTTP 요청을 통해 Auth API 엔드포인트를 테스트합니다.
 * - 전체 Spring Context 로드
 * - 실제 서버 기동 (RANDOM_PORT)
 * - TestRestTemplate을 통한 HTTP 호출
 * - 실제 데이터베이스 사용
 *
 * 실행 전 요구사항:
 * - docker compose up -d (MySQL·Redis — local 프로필 값)
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.auth.AuthE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class AuthE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] AuthE2ETest ${janitor.cleanup(resources)}") }

    // 테스트용 고유 사용자 정보 생성
    // 시드 접두사(e2e_test_)와 겹치지 않게 e2e_auth_ — signup 검증 대상이라 헬퍼 대신 inline, 생성 즉시 정리 대상으로 등록
    fun uniqueLoginId() = resources.user("e2e_auth_${UUID.randomUUID().toString().take(8)}")
    fun uniqueEmail() = "e2e_${UUID.randomUUID().toString().take(8)}@example.com"

    describe("POST /auth/signup - 회원가입") {

        context("유효한 데이터로 회원가입 요청") {
            it("201 Created와 UserInfoDto를 반환한다") {
                // given
                val loginId = uniqueLoginId()
                val email = uniqueEmail()
                val request = SignupRequestDto(
                    loginId = loginId,
                    password = "Test@1234!",
                    email = email,
                    name = "E2E 테스트 사용자",
                    nickname = "E2E테스터",
                    phone = "010-1234-5678",
                )

                // when
                val response = restTemplate.postJson<UserInfoDto>("/auth/signup", request)

                // then
                response.statusCode shouldBe HttpStatus.CREATED
                response.body.shouldNotBeNull()
                response.body!!.loginId shouldBe loginId
                response.body!!.email shouldBe email
                response.body!!.groups.shouldNotBeNull()
            }
        }

        context("중복된 로그인 ID로 회원가입 요청") {
            it("409 Conflict를 반환한다") {
                // given - 먼저 사용자 생성
                val loginId = uniqueLoginId()
                val firstRequest = SignupRequestDto(
                    loginId = loginId,
                    password = "Test@1234!",
                    email = uniqueEmail(),
                )
                restTemplate.postJson<UserInfoDto>("/auth/signup", firstRequest)

                // when - 같은 loginId로 다시 회원가입 시도
                val duplicateRequest = SignupRequestDto(
                    loginId = loginId,
                    password = "Test@1234!",
                    email = uniqueEmail(),
                )
                val response = restTemplate.postJson<String>("/auth/signup", duplicateRequest)

                // then
                response.statusCode shouldBe HttpStatus.CONFLICT
            }
        }

        context("짧은 비밀번호로 회원가입 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val request = SignupRequestDto(
                    loginId = uniqueLoginId(),
                    password = "1234567", // 8자 미만
                )

                // when
                val response = restTemplate.postJson<String>("/auth/signup", request)

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("POST /auth/login - 로그인") {

        context("유효한 자격 증명으로 로그인") {
            it("200 OK와 토큰을 반환한다") {
                // given - 먼저 사용자 생성
                val loginId = uniqueLoginId()
                val password = "Test@1234!"
                val signupRequest = SignupRequestDto(
                    loginId = loginId,
                    password = password,
                )
                restTemplate.postJson<UserInfoDto>("/auth/signup", signupRequest)

                // when
                val loginRequest = LoginRequestDto(
                    loginId = loginId,
                    password = password,
                )
                val response = restTemplate.postJson<TokenResponseDto>("/auth/login", loginRequest)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!.accessToken.shouldNotBeBlank()
                response.body!!.refreshToken.shouldNotBeBlank()
                response.body!!.tokenType shouldBe "Bearer"
            }
        }

        context("잘못된 비밀번호로 로그인") {
            it("401 Unauthorized를 반환한다") {
                // given - 먼저 사용자 생성
                val loginId = uniqueLoginId()
                val signupRequest = SignupRequestDto(
                    loginId = loginId,
                    password = "Test@1234!",
                )
                restTemplate.postJson<UserInfoDto>("/auth/signup", signupRequest)

                // when
                val loginRequest = LoginRequestDto(
                    loginId = loginId,
                    password = "WrongPassword!",
                )
                val response = restTemplate.postJson<String>("/auth/login", loginRequest)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("존재하지 않는 사용자로 로그인") {
            it("401 Unauthorized를 반환한다") {
                // given
                val loginRequest = LoginRequestDto(
                    loginId = "nonexistent_user_${UUID.randomUUID()}",
                    password = "Test@1234!",
                )

                // when
                val response = restTemplate.postJson<String>("/auth/login", loginRequest)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("POST /auth/refresh - 토큰 갱신") {

        context("유효한 리프레시 토큰으로 갱신 요청") {
            it("200 OK와 새 액세스 토큰을 반환한다") {
                // given - 사용자 생성 및 로그인
                val loginId = uniqueLoginId()
                val password = "Test@1234!"
                val signupRequest = SignupRequestDto(loginId = loginId, password = password)
                restTemplate.postJson<UserInfoDto>("/auth/signup", signupRequest)

                val loginRequest = LoginRequestDto(loginId = loginId, password = password)
                val loginResponse = restTemplate.postJson<TokenResponseDto>("/auth/login", loginRequest)
                val refreshToken = loginResponse.body!!.refreshToken

                // when
                val refreshRequest = RefreshTokenRequestDto(refreshToken = refreshToken)
                val response = restTemplate.postJson<AccessTokenResponseDto>("/auth/refresh", refreshRequest)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!.accessToken.shouldNotBeBlank()
                response.body!!.tokenType shouldBe "Bearer"
            }
        }

        context("잘못된 리프레시 토큰으로 갱신 요청") {
            it("401 Unauthorized를 반환한다") {
                // given
                val refreshRequest = RefreshTokenRequestDto(refreshToken = "invalid-refresh-token")

                // when
                val response = restTemplate.postJson<String>("/auth/refresh", refreshRequest)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("GET /users/me - 내 정보 조회") {

        context("유효한 토큰으로 조회") {
            it("200 OK와 사용자 정보를 반환한다") {
                // given - 사용자 생성 및 로그인
                val loginId = uniqueLoginId()
                val password = "Test@1234!"
                val signupRequest = SignupRequestDto(
                    loginId = loginId,
                    password = password,
                    name = "테스트",
                )
                restTemplate.postJson<UserInfoDto>("/auth/signup", signupRequest)

                val loginRequest = LoginRequestDto(loginId = loginId, password = password)
                val loginResponse = restTemplate.postJson<TokenResponseDto>("/auth/login", loginRequest)
                val accessToken = loginResponse.body!!.accessToken

                // when
                val response = restTemplate.getWithAuth<UserInfoDto>("/users/me", accessToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!.loginId shouldBe loginId
            }
        }

        context("토큰 없이 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/users/me", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("POST /auth/logout - 로그아웃") {

        context("유효한 토큰으로 로그아웃") {
            it("204 No Content를 반환하고 토큰이 무효화된다") {
                // given - 사용자 생성 및 로그인
                val loginId = uniqueLoginId()
                val password = "Test@1234!"
                val signupRequest = SignupRequestDto(loginId = loginId, password = password)
                restTemplate.postJson<UserInfoDto>("/auth/signup", signupRequest)

                val loginRequest = LoginRequestDto(loginId = loginId, password = password)
                val loginResponse = restTemplate.postJson<TokenResponseDto>("/auth/login", loginRequest)
                val accessToken = loginResponse.body!!.accessToken

                // when
                val logoutResponse = restTemplate.postWithAuth<Void>("/auth/logout", accessToken, emptyMap<String, Any>())

                // then
                logoutResponse.statusCode shouldBe HttpStatus.NO_CONTENT

                // 로그아웃 후 토큰으로 API 호출 시 실패해야 함
                val meResponse = restTemplate.getWithAuth<String>("/users/me", accessToken)
                meResponse.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("전체 인증 흐름 테스트") {

        context("회원가입 → 로그인 → 토큰 갱신 → 내 정보 조회 → 로그아웃") {
            it("전체 흐름이 정상 동작한다") {
                // 1. 회원가입
                val loginId = uniqueLoginId()
                val password = "Test@1234!"
                val signupRequest = SignupRequestDto(
                    loginId = loginId,
                    password = password,
                    email = uniqueEmail(),
                    name = "통합 테스트 사용자",
                )
                val signupResponse = restTemplate.postJson<UserInfoDto>("/auth/signup", signupRequest)
                signupResponse.statusCode shouldBe HttpStatus.CREATED

                // 2. 로그인
                val loginRequest = LoginRequestDto(loginId = loginId, password = password)
                val loginResponse = restTemplate.postJson<TokenResponseDto>("/auth/login", loginRequest)
                loginResponse.statusCode shouldBe HttpStatus.OK
                val tokens = loginResponse.body!!

                // 3. 토큰 갱신
                val refreshRequest = RefreshTokenRequestDto(refreshToken = tokens.refreshToken)
                val refreshResponse = restTemplate.postJson<AccessTokenResponseDto>("/auth/refresh", refreshRequest)
                refreshResponse.statusCode shouldBe HttpStatus.OK
                val newAccessToken = refreshResponse.body!!.accessToken

                // 4. 새 토큰으로 내 정보 조회
                val meResponse = restTemplate.getWithAuth<UserInfoDto>("/users/me", newAccessToken)
                meResponse.statusCode shouldBe HttpStatus.OK
                meResponse.body!!.loginId shouldBe loginId

                // 5. 로그아웃
                val logoutResponse = restTemplate.postWithAuth<Void>(
                    "/auth/logout",
                    newAccessToken,
                    emptyMap<String, Any>(),
                )
                logoutResponse.statusCode shouldBe HttpStatus.NO_CONTENT

                // 6. 로그아웃 후 토큰 무효화 확인
                val afterLogoutResponse = restTemplate.getWithAuth<String>("/users/me", newAccessToken)
                afterLogoutResponse.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }
})