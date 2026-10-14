package com.rex.api.pub.skeleton.user

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.LoginRequestDto
import com.rex.api.pub.skeleton.dto.auth.TokenResponseDto
import com.rex.api.pub.skeleton.dto.user.EmailVerificationConfirmDto
import com.rex.api.pub.skeleton.dto.user.EmailVerificationRequestDto
import com.rex.api.pub.skeleton.dto.user.PasswordChangeDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetConfirmDto
import com.rex.api.pub.skeleton.dto.user.PasswordResetRequestDto
import com.rex.api.pub.skeleton.dto.user.UserProfileUpdateDto
import com.rex.api.pub.skeleton.model.user.UserProfileModel
import com.rex.api.pub.skeleton.model.user.UserSessionModel
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import com.rex.api.pub.skeleton.support.putWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * User Self Service API E2E 테스트.
 *
 * 사용자 셀프 서비스 API를 실제 HTTP 요청으로 테스트합니다.
 * - 프로필 조회/수정
 * - 비밀번호 변경
 * - 비밀번호 재설정
 * - 이메일 인증
 * - 세션 관리
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.user.UserSelfServiceE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class UserSelfServiceE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] UserSelfServiceE2ETest ${janitor.cleanup(resources)}") }

    // 테스트용 고유 사용자 정보 생성
    fun uniqueLoginId() = "e2e_user_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_${UUID.randomUUID().toString().take(8)}@example.com"

    // 테스트용 사용자 생성 및 로그인 헬퍼
    // (accessToken, loginId) — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(
        loginId: String = uniqueLoginId(),
        password: String = "Test@1234!",
        email: String = uniqueEmail(),
    ): Pair<String, String> =
        restTemplate.createAndLoginUser(resources, "e2e_user_", password = password, email = email, name = "테스트 사용자", loginId = loginId)
            .let { it.accessToken to it.loginId }

    describe("GET /user/profile - 프로필 조회") {

        context("인증된 사용자가 프로필 조회") {
            it("200 OK와 프로필 정보를 반환한다") {
                // given
                val (accessToken, _) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<UserProfileModel>("/user/profile", accessToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
            }
        }

        context("인증 없이 프로필 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/user/profile", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("PUT /user/profile - 프로필 수정") {

        context("유효한 데이터로 프로필 수정") {
            it("200 OK와 수정된 프로필을 반환한다") {
                // given
                val (accessToken, _) = createAndLoginUser()
                val updateRequest = UserProfileUpdateDto(
                    name = "수정된 이름",
                    nickname = "수정된닉네임",
                    phone = "010-9999-8888",
                    profileImage = null,
                )

                // when
                val response = restTemplate.putWithAuth<UserProfileModel>(
                    "/user/profile",
                    accessToken,
                    updateRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!.name shouldBe "수정된 이름"
            }
        }
    }

    describe("POST /user/password/change - 비밀번호 변경") {

        context("올바른 현재 비밀번호로 변경 요청") {
            it("200 OK를 반환하고 새 비밀번호로 로그인 가능") {
                // given
                val loginId = uniqueLoginId()
                val oldPassword = "Test@1234!"
                val newPassword = "NewTest@5678!"
                val (accessToken, _) = createAndLoginUser(loginId = loginId, password = oldPassword)

                val changeRequest = PasswordChangeDto(
                    currentPassword = oldPassword,
                    newPassword = newPassword,
                )

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>(
                    "/user/password/change",
                    accessToken,
                    changeRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK

                // 새 비밀번호로 로그인 가능 확인
                val newLoginRequest = LoginRequestDto(loginId = loginId, password = newPassword)
                val newLoginResponse = restTemplate.postJson<TokenResponseDto>("/auth/login", newLoginRequest)
                newLoginResponse.statusCode shouldBe HttpStatus.OK
            }
        }

        context("잘못된 현재 비밀번호로 변경 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val (accessToken, _) = createAndLoginUser()
                val changeRequest = PasswordChangeDto(
                    currentPassword = "WrongPassword!",
                    newPassword = "NewTest@5678!",
                )

                // when
                val response = restTemplate.postWithAuth<String>(
                    "/user/password/change",
                    accessToken,
                    changeRequest,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("POST /user/password/reset-request - 비밀번호 재설정 요청") {

        context("존재하는 이메일로 재설정 요청") {
            it("200 OK를 반환한다 (이메일 전송 시도)") {
                // given
                val email = uniqueEmail()
                createAndLoginUser(email = email)
                val request = PasswordResetRequestDto(email = email)

                // when
                val response = restTemplate.postJson<String>("/user/password/reset-request", request)

                // then — 500 을 허용하던 관용은 auth 트랜잭션 매니저 미지정(@Modifying → TransactionRequiredException) 을 덮고 있었다
                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("존재하지 않는 이메일로 재설정 요청") {
            it("200 OK를 반환한다 (보안상 동일 응답)") {
                // given
                val request = PasswordResetRequestDto(email = "nonexistent@example.com")

                // when
                val response = restTemplate.postJson<String>("/user/password/reset-request", request)

                // then — 500 을 허용하던 관용은 auth 트랜잭션 매니저 미지정(@Modifying → TransactionRequiredException) 을 덮고 있었다
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("POST /user/email/verify-request - 이메일 인증 요청") {

        context("인증된 사용자가 인증 이메일을 요청") {
            it("200 OK 를 반환한다 (토큰 무효화·저장이 auth 트랜잭션에서 일어난다)") {
                // given
                val (accessToken, _) = createAndLoginUser()
                val request = EmailVerificationRequestDto(email = uniqueEmail())

                // when
                val response = restTemplate.postWithAuth<Map<String, Any>>("/user/email/verify-request", accessToken, request)

                // then
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("POST /user/password/reset - 비밀번호 재설정 완료") {

        context("유효하지 않은 토큰으로 재설정 시도") {
            it("400 Bad Request를 반환한다") {
                // given
                val request = PasswordResetConfirmDto(
                    token = "invalid-reset-token",
                    newPassword = "NewPassword@123",
                )

                // when
                val response = restTemplate.postJson<Map<String, Any>>("/user/password/reset", request)

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("POST /user/email/verify - 이메일 인증") {

        context("유효하지 않은 토큰으로 인증 시도") {
            it("400 Bad Request를 반환한다") {
                // given
                val request = EmailVerificationConfirmDto(token = "invalid-email-token")

                // when
                val response = restTemplate.postJson<Map<String, Any>>("/user/email/verify", request)

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("GET /user/sessions - 세션 목록 조회") {

        context("인증된 사용자가 세션 목록 조회") {
            it("200 OK와 세션 목록을 반환한다") {
                // given
                val (accessToken, _) = createAndLoginUser()

                // when
                // List<T> 는 타입 소거로 LinkedHashMap 이 되므로 Array 로 받는다
                val response = restTemplate.getWithAuth<Array<UserSessionModel>>("/user/sessions", accessToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                val sessions = response.body.shouldNotBeNull()
                // is-prefix Boolean 회귀 — AuthMapper 의 currentSession→isCurrentSession 명시가 없으면 전부 false 로 내려간다
                sessions.count { it.isCurrentSession } shouldBe 1
            }
        }
    }

    describe("DELETE /user/sessions/{sessionId} - 특정 세션 종료") {

        context("존재하지 않는 세션 종료 시도") {
            it("400 Bad Request를 반환한다") {
                // given
                val (accessToken, _) = createAndLoginUser()

                // when
                val response = restTemplate.deleteWithAuth<String>("/user/sessions/99999999", accessToken)

                // then
                // 컨트롤러는 유효하지 않은 세션에 대해 400 반환
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("DELETE /user/sessions - 모든 세션 종료") {

        context("현재 세션 제외 모든 세션 종료") {
            it("200 OK와 종료된 세션 수를 반환한다") {
                // given
                val (accessToken, _) = createAndLoginUser()

                // when
                val response = restTemplate.deleteWithAuth<String>("/user/sessions", accessToken)

                // then
                // 컨트롤러는 ResultResponseModel과 함께 200 OK 반환
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }
})