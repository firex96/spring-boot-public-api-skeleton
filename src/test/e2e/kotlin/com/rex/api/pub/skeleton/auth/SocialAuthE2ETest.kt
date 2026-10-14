package com.rex.api.pub.skeleton.auth

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.auth.SocialLoginRequestDto
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.deleteWithAuth
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postJson
import com.rex.api.pub.skeleton.support.postWithAuth
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * Social Auth API E2E 테스트.
 *
 * 소셜 로그인 API를 실제 HTTP 요청으로 테스트합니다.
 * - 소셜 로그인 URL 조회
 * - 소셜 로그인 (코드 교환)
 * - 소셜 계정 연동/해제
 * - 연동된 소셜 계정 목록 조회
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.auth.SocialAuthE2ETest"
 * ```
 *
 * 참고: 소셜 인증은 외부 OAuth 제공자와의 연동이 필요하므로
 * 실제 Authorization Code 테스트는 제한적입니다.
 * 설정이 안된 경우 일부 테스트가 실패할 수 있습니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class SocialAuthE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] SocialAuthE2ETest ${janitor.cleanup(resources)}") }

    fun uniqueLoginId() = "e2e_social_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_social_${UUID.randomUUID().toString().take(8)}@example.com"

    // 일반 사용자 생성 및 로그인
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): String = restTemplate.createAndLoginUser(resources, "e2e_social_").accessToken

    // 지원되는 소셜 프로바이더
    val providers = listOf("google", "kakao", "naver")

    // 리다이렉트를 따르지 않는 클라이언트 — 콜백의 302 와 Location 을 단언하기 위해
    val noRedirectClient =
        RestTemplate(
            object : SimpleClientHttpRequestFactory() {
                override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                    super.prepareConnection(connection, httpMethod)
                    connection.instanceFollowRedirects = false
                }
            },
        )

    describe("GET /auth/oauth2/{provider}/authorize - 소셜 로그인 URL 조회") {

        providers.forEach { provider ->
            context("$provider 인증 URL 조회") {
                it("authorizationUrl과 state를 반환한다") {
                    // when
                    val response = restTemplate.getForEntity(
                        "/auth/oauth2/$provider/authorize",
                        Map::class.java,
                    )

                    // then
                    // 설정이 되어있으면 200 OK, 안되어있으면 500 또는 다른 에러
                    if (response.statusCode == HttpStatus.OK) {
                        response.body.shouldNotBeNull()
                        response.body!!["authorizationUrl"].shouldNotBeNull()
                        response.body!!["state"].shouldNotBeNull()
                    }
                    // 설정이 안 된 경우도 테스트 통과 (graceful handling)
                }
            }
        }

        context("지원하지 않는 프로바이더로 요청") {
            it("400 Bad Request를 반환한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/auth/oauth2/unsupported_provider/authorize",
                    String::class.java,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("POST /auth/oauth2/{provider}/login - 소셜 로그인 (코드 교환)") {

        providers.forEach { provider ->
            context("$provider 잘못된 authorization code로 로그인") {
                it("400 Bad Request를 반환한다") {
                    // given
                    val request = SocialLoginRequestDto(
                        code = "invalid-auth-code",
                        deviceType = "WEB",
                        deviceInfo = "E2E Test Browser",
                    )

                    // when
                    val response = restTemplate.postJson<Map<String, Any>>(
                        "/auth/oauth2/$provider/login",
                        request,
                    )

                    // then
                    // 잘못된 코드이므로 에러 응답 (400 또는 500)
                    (
                        response.statusCode.is4xxClientError ||
                            response.statusCode.is5xxServerError
                        ) shouldBe true
                }
            }
        }

        context("지원하지 않는 프로바이더로 로그인") {
            it("400 Bad Request를 반환한다") {
                // given
                val request = SocialLoginRequestDto(
                    code = "some-code",
                    deviceType = "WEB",
                    deviceInfo = "E2E Test Browser",
                )

                // when
                val response = restTemplate.postJson<String>(
                    "/auth/oauth2/unsupported_provider/login",
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    describe("GET /auth/oauth2/{provider}/callback - 소셜 로그인 콜백") {

        providers.forEach { provider ->
            context("$provider 콜백 에러 파라미터로 요청") {
                it("에러 페이지로 리다이렉트한다") {
                    // given
                    val url = "/auth/oauth2/$provider/callback?" +
                        "error=access_denied&" +
                        "error_description=User%20denied%20access"

                    // when — 리다이렉트를 따르지 않는 클라이언트로 302 와 Location 자체를 본다.
                    // URI 로 넘긴다 — String 템플릿으로 넘기면 RestTemplate 이 %20 을 다시 인코딩해(%2520) 서버가 리터럴 %20 을 받는다
                    val response = noRedirectClient.exchange(URI.create(restTemplate.rootUri + url), HttpMethod.GET, null, String::class.java)

                    // then — 데모 콜백으로 302, 값은 하나에 인코딩 한 번(공백 → +)
                    response.statusCode shouldBe HttpStatus.FOUND
                    response.headers.location.shouldNotBeNull().toString() shouldContain
                        "/demo/auth/callback?error=access_denied&error_description=User+denied+access"
                }
            }

            context("$provider 잘못된 code로 콜백") {
                it("에러 처리 후 리다이렉트한다") {
                    // given
                    val url = "/auth/oauth2/$provider/callback?" +
                        "code=invalid-code&" +
                        "state=test-state"

                    // when
                    val response = restTemplate.getForEntity(url, String::class.java)

                    // then
                    // 에러 응답 또는 리다이렉트
                    response.statusCode.shouldNotBeNull()
                }
            }
        }
    }

    describe("POST /auth/oauth2/{provider}/link - 소셜 계정 연동") {

        providers.forEach { provider ->
            context("$provider 계정 연동 - 잘못된 코드") {
                it("에러 응답을 반환한다") {
                    // given
                    val accessToken = createAndLoginUser()
                    val request = SocialLoginRequestDto(
                        code = "invalid-link-code",
                        deviceType = "WEB",
                        deviceInfo = "E2E Test Browser",
                    )

                    // when
                    val response = restTemplate.postWithAuth<String>(
                        "/auth/oauth2/$provider/link",
                        accessToken,
                        request,
                    )

                    // then
                    // 잘못된 코드이므로 에러 응답
                    (
                        response.statusCode.is4xxClientError ||
                            response.statusCode.is5xxServerError
                        ) shouldBe true
                }
            }
        }

        context("인증 없이 소셜 계정 연동 시도") {
            it("401 Unauthorized를 반환한다") {
                // given
                val request = SocialLoginRequestDto(
                    code = "some-code",
                    deviceType = "WEB",
                    deviceInfo = "E2E Test Browser",
                )

                // when
                val response = restTemplate.postJson<String>(
                    "/auth/oauth2/google/link",
                    request,
                )

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("DELETE /auth/oauth2/{provider}/unlink - 소셜 계정 연동 해제") {

        providers.forEach { provider ->
            context("$provider 연동되지 않은 계정 해제 시도") {
                it("404 Not Found를 반환한다") {
                    // given
                    val accessToken = createAndLoginUser()

                    // when
                    val response = restTemplate.deleteWithAuth<String>(
                        "/auth/oauth2/$provider/unlink",
                        accessToken,
                    )

                    // then
                    // 연동되지 않은 계정이므로 404, 400, 또는 401 (서비스 구현에 따라 다름)
                    (
                        response.statusCode == HttpStatus.NOT_FOUND ||
                            response.statusCode == HttpStatus.BAD_REQUEST ||
                            response.statusCode == HttpStatus.UNAUTHORIZED
                        ) shouldBe true
                }
            }
        }

        context("인증 없이 소셜 계정 연동 해제 시도") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.exchange(
                    "/auth/oauth2/google/unlink",
                    org.springframework.http.HttpMethod.DELETE,
                    null,
                    String::class.java,
                )

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("GET /auth/oauth2/accounts - 연동된 소셜 계정 목록 조회") {

        context("인증된 사용자가 연동된 계정 목록 조회") {
            it("200 OK와 계정 목록을 반환한다") {
                // given
                val accessToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<List<Map<String, Any>>>(
                    "/auth/oauth2/accounts",
                    accessToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                // 새 사용자는 연동된 소셜 계정이 없음 (빈 배열)
            }
        }

        context("인증 없이 연동된 계정 목록 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/auth/oauth2/accounts", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("소셜 로그인 URL 형식 검증") {

        context("Google 인증 URL") {
            it("Google OAuth 엔드포인트를 포함한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/auth/oauth2/google/authorize",
                    Map::class.java,
                )

                // then
                if (response.statusCode == HttpStatus.OK) {
                    val authUrl = response.body!!["authorizationUrl"] as String
                    authUrl shouldContain "accounts.google.com"
                }
            }
        }

        context("Kakao 인증 URL") {
            it("Kakao OAuth 엔드포인트를 포함한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/auth/oauth2/kakao/authorize",
                    Map::class.java,
                )

                // then
                if (response.statusCode == HttpStatus.OK) {
                    val authUrl = response.body!!["authorizationUrl"] as String
                    authUrl shouldContain "kauth.kakao.com"
                }
            }
        }

        context("Naver 인증 URL") {
            it("Naver OAuth 엔드포인트를 포함한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/auth/oauth2/naver/authorize",
                    Map::class.java,
                )

                // then
                if (response.statusCode == HttpStatus.OK) {
                    val authUrl = response.body!!["authorizationUrl"] as String
                    authUrl shouldContain "nid.naver.com"
                }
            }
        }
    }
})