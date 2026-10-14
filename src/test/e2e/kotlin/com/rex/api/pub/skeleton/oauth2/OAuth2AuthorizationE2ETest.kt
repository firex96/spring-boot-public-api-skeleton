package com.rex.api.pub.skeleton.oauth2

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.dto.oauth2.CreateOAuth2ClientRequestDto
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2AuthorizationCodeRepository
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postForm
import com.rex.api.pub.skeleton.support.postWithAuth
import com.rex.api.pub.skeleton.support.superAdminToken
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.net.HttpURLConnection
import java.util.UUID

/**
 * OAuth2 Authorization API E2E 테스트.
 *
 * OAuth2 인증 서버 API를 실제 HTTP 요청으로 테스트합니다.
 * - Authorization Code Flow
 * - Token 발급 (authorization_code, refresh_token, client_credentials)
 * - Token Introspection
 * - Token Revocation
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.oauth2.OAuth2AuthorizationE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class OAuth2AuthorizationE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
    private val oAuth2AuthorizationCodeRepository: OAuth2AuthorizationCodeRepository,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] OAuth2AuthorizationE2ETest ${janitor.cleanup(resources)}") }

    // 리다이렉트를 따르지 않는 클라이언트 — 302 와 Location 자체를 단언하기 위해
    val noRedirectClient =
        RestTemplate(
            object : SimpleClientHttpRequestFactory() {
                override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                    super.prepareConnection(connection, httpMethod)
                    connection.instanceFollowRedirects = false
                }
            },
        ).apply {
            // 4xx 도 예외가 아니라 응답으로 받는다 — JSON 실패(401/400)와 Location 부재를 단언한다
            errorHandler = object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean = false
            }
        }

    /** 브라우저 없이 동의 화면 흐름을 흉내낸다 — 로그인 사용자 토큰으로 GET/POST /oauth2/consent. */
    fun consentGet(userToken: String, query: String) =
        noRedirectClient.exchange(
            restTemplate.rootUri + "/oauth2/consent?$query",
            HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(userToken) }),
            String::class.java,
        )

    fun consentPost(userToken: String, form: LinkedMultiValueMap<String, String>) =
        noRedirectClient.exchange(
            restTemplate.rootUri + "/oauth2/consent",
            HttpMethod.POST,
            HttpEntity(
                form,
                HttpHeaders().apply {
                    setBearerAuth(userToken)
                    contentType = MediaType.APPLICATION_FORM_URLENCODED
                },
            ),
            String::class.java,
        )

    fun consentForm(clientId: String, redirectUri: String, approve: Boolean, state: String = "consent-state") =
        LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("redirect_uri", redirectUri)
            add("scope", "openid profile email")
            add("state", state)
            add("approve", approve.toString())
        }

    fun codeCount(clientId: String) = oAuth2AuthorizationCodeRepository.findAll().count { it.clientId == clientId }

    fun uniqueLoginId() = "e2e_oauth2_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_oauth2_${UUID.randomUUID().toString().take(8)}@example.com"
    fun uniqueClientName() = "E2E OAuth2 Client ${UUID.randomUUID().toString().take(8)}"

    // 일반 사용자 생성 및 로그인
    // (accessToken, loginId) — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): Pair<String, String> =
        restTemplate.createAndLoginUser(resources, "e2e_oauth2_").let { it.accessToken to it.loginId }

    // Admin 토큰 — 시드 e2e_test_super_admin(모든 권한). 로그인 실패는 실패다(일반 사용자 폴백 없음)
    fun getAdminToken(): String = restTemplate.superAdminToken()

    // OAuth2 클라이언트 생성 (Admin 권한 필요)
    fun createOAuth2Client(adminToken: String, isFirstParty: Boolean = true, requirePkce: Boolean = false): Pair<String, String> {
        val request = CreateOAuth2ClientRequestDto(
            clientName = uniqueClientName(),
            description = "E2E 테스트용 OAuth2 클라이언트",
            grantTypes = listOf("authorization_code", "refresh_token", "client_credentials"),
            redirectUris = listOf("http://localhost:8080/callback"),
            scopes = listOf("openid", "profile", "email"),
            accessTokenValidity = 3600,
            refreshTokenValidity = 604800,
            isFirstParty = isFirstParty,
            requirePkce = requirePkce,
        )

        val response = restTemplate.postWithAuth<Map<String, Any>>(
            "/admin/oauth2/clients",
            adminToken,
            request,
        )

        response.statusCode shouldBe HttpStatus.CREATED
        val clientId = resources.oauth2Client(response.body.shouldNotBeNull()["clientId"] as String)
        val clientSecret = response.body.shouldNotBeNull()["clientSecret"] as String
        return Pair(clientId, clientSecret)
    }

    describe("POST /oauth2/token - 토큰 발급") {

        context("client_credentials grant type") {
            it("Access Token을 반환한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, clientSecret) = client
                val params = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "client_credentials")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                    add("scope", "openid profile")
                }

                // when
                val response = restTemplate.postForm<Map<String, Any>>("/oauth2/token", params)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["access_token"].shouldNotBeNull()
                response.body!!["token_type"] shouldBe "Bearer"
            }
        }

        context("잘못된 client_id로 요청") {
            it("401 Unauthorized를 반환한다") {
                // given
                val params = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "client_credentials")
                    add("client_id", "invalid-client-id")
                    add("client_secret", "invalid-secret")
                }

                // when
                val response = restTemplate.postForm<Map<String, Any>>("/oauth2/token", params)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("잘못된 grant_type으로 요청") {
            it("400 Bad Request를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, clientSecret) = client
                val params = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "unsupported_grant_type")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                // when
                val response = restTemplate.postForm<Map<String, Any>>("/oauth2/token", params)

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        context("client_credentials grant type 의 refresh_token") {
            it("client_credentials 응답에는 refresh_token 이 없다 — refresh grant 는 authorization_code 흐름(POST /oauth2/consent)에서 검증한다") {
                // given
                val adminToken = getAdminToken()
                val (clientId, clientSecret) = createOAuth2Client(adminToken)
                val tokenParams = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "client_credentials")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                    add("scope", "openid profile")
                }

                // when
                val tokenResponse = restTemplate.postForm<Map<String, Any>>("/oauth2/token", tokenParams)

                // then — 사용자 컨텍스트가 없는 grant 라 refresh_token 을 내지 않는다(RFC 6749 §4.4.3)
                tokenResponse.statusCode shouldBe HttpStatus.OK
                tokenResponse.body.shouldNotBeNull()["access_token"].shouldNotBeNull()
                tokenResponse.body.shouldNotBeNull()["refresh_token"] shouldBe null
            }
        }
    }

    describe("POST /oauth2/introspect - 토큰 검증") {

        context("유효한 토큰 검증") {
            it("active: true를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, clientSecret) = client

                // 먼저 토큰 발급
                val tokenParams = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "client_credentials")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                val tokenResponse = restTemplate.postForm<Map<String, Any>>("/oauth2/token", tokenParams)

                tokenResponse.statusCode shouldBe HttpStatus.OK
                val accessToken = tokenResponse.body!!["access_token"] as String

                // 토큰 검증
                val introspectParams = LinkedMultiValueMap<String, String>().apply {
                    add("token", accessToken)
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                // when
                val response = restTemplate.postForm<Map<String, Any>>("/oauth2/introspect", introspectParams)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["active"] shouldBe true
            }
        }

        context("유효하지 않은 토큰 검증") {
            it("active: false를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, clientSecret) = client

                val introspectParams = LinkedMultiValueMap<String, String>().apply {
                    add("token", "invalid-token")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                // when
                val response = restTemplate.postForm<Map<String, Any>>("/oauth2/introspect", introspectParams)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()
                response.body!!["active"] shouldBe false
            }
        }
    }

    describe("POST /oauth2/revoke - 토큰 취소") {

        context("유효한 토큰 취소") {
            it("200 OK를 반환하고 토큰이 무효화된다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, clientSecret) = client

                // 먼저 토큰 발급
                val tokenParams = LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "client_credentials")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                val tokenResponse = restTemplate.postForm<Map<String, Any>>("/oauth2/token", tokenParams)

                tokenResponse.statusCode shouldBe HttpStatus.OK
                val accessToken = tokenResponse.body!!["access_token"] as String

                // 토큰 취소
                val revokeParams = LinkedMultiValueMap<String, String>().apply {
                    add("token", accessToken)
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                // when
                val response = restTemplate.postForm<Void>("/oauth2/revoke", revokeParams)

                // then
                response.statusCode shouldBe HttpStatus.OK

                // 취소 후 검증하면 active: false
                val introspectParams = LinkedMultiValueMap<String, String>().apply {
                    add("token", accessToken)
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }
                val introspectResponse = restTemplate.postForm<Map<String, Any>>("/oauth2/introspect", introspectParams)
                introspectResponse.body!!["active"] shouldBe false
            }
        }

        context("존재하지 않는 토큰 취소") {
            it("200 OK를 반환한다 (RFC 7009)") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, clientSecret) = client

                val revokeParams = LinkedMultiValueMap<String, String>().apply {
                    add("token", "non-existent-token")
                    add("client_id", clientId)
                    add("client_secret", clientSecret)
                }

                // when
                val response = restTemplate.postForm<Void>("/oauth2/revoke", revokeParams)

                // then
                // RFC 7009: 토큰이 존재하지 않아도 200 OK 반환
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    describe("GET /oauth2/authorize - Authorization 요청") {

        context("인증되지 않은 사용자가 요청") {
            it("로그인 페이지로 리다이렉트한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, _) = client
                val url = "/oauth2/authorize?" +
                    "response_type=code&" +
                    "client_id=$clientId&" +
                    "redirect_uri=http://localhost:8080/callback&" +
                    "scope=openid profile&" +
                    "state=test-state"

                // when — 리다이렉트를 따라가면 POST 전용 /auth/login 에 GET 이 닿아 405 가 된다. 계약은 302 + Location 이다
                val response = noRedirectClient.exchange(restTemplate.rootUri + url, HttpMethod.GET, null, String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.FOUND
                val location = response.headers.location.shouldNotBeNull().toString()
                // Tomcat 은 sendRedirect 의 상대 경로를 절대 URL 로 만든다 — 경로·쿼리로 단언
                location shouldContain "/auth/login?redirect_to=/oauth2/authorize&response_type=code&client_id=$clientId"
                location shouldContain "redirect_uri="
                location shouldContain "scope="
                location shouldContain "state=test-state"
            }
        }

        context("잘못된 client_id로 요청") {
            it("401 JSON 으로 끝나고 리다이렉트하지 않는다 (RFC 6749 §4.1.2.1 — client 가 잘못되면 redirect_uri 를 신뢰할 수 없다)") {
                // given
                val url = "/oauth2/authorize?" +
                    "response_type=code&" +
                    "client_id=invalid-client-id&" +
                    "redirect_uri=http://localhost:8080/callback&" +
                    "scope=openid profile"

                // when
                val response = noRedirectClient.exchange(restTemplate.rootUri + url, HttpMethod.GET, null, String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                response.headers.location shouldBe null
                response.body.shouldNotBeNull() shouldContain "invalid_client"
            }
        }

        context("등록되지 않은 redirect_uri 로 요청") {
            it("400 JSON 으로 끝나고 그 URI 로 리다이렉트하지 않는다") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken)
                val url = "/oauth2/authorize?" +
                    "response_type=code&" +
                    "client_id=$clientId&" +
                    "redirect_uri=https://evil.example/cb&" +
                    "scope=openid"

                // when
                val response = noRedirectClient.exchange(restTemplate.rootUri + url, HttpMethod.GET, null, String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.headers.location shouldBe null
                response.body.shouldNotBeNull() shouldContain "invalid_request"
            }
        }

        context("등록 client 가 지원하지 않는 scope 로 요청") {
            it("검증된 redirect_uri 로 error=invalid_scope 리다이렉트한다") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken)
                val url = "/oauth2/authorize?" +
                    "response_type=code&" +
                    "client_id=$clientId&" +
                    "redirect_uri=http://localhost:8080/callback&" +
                    "scope=admin&" +
                    "state=scope-state"

                // when
                val response = noRedirectClient.exchange(restTemplate.rootUri + url, HttpMethod.GET, null, String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.FOUND
                val location = response.headers.location.shouldNotBeNull().toString()
                location shouldStartWith "http://localhost:8080/callback?error=invalid_scope"
                location shouldContain "state=scope-state"
            }
        }

        context("인증된 사용자가 first-party 앱 요청") {
            it("Authorization Code를 발급하여 리다이렉트한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)
                val (userToken, _) = createAndLoginUser()

                val (clientId, _) = client
                val url = "/oauth2/authorize?" +
                    "response_type=code&" +
                    "client_id=$clientId&" +
                    "redirect_uri=http://localhost:8080/callback&" +
                    "scope=openid profile&" +
                    "state=test-state"

                // when — 리다이렉트를 따르지 않는 클라이언트에 Bearer 토큰만 얹는다
                val headers = HttpHeaders().apply { setBearerAuth(userToken) }
                val response =
                    noRedirectClient.exchange(restTemplate.rootUri + url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

                // then — first-party 앱은 동의 없이 code 를 발급해 redirect_uri 로 보낸다
                response.statusCode shouldBe HttpStatus.FOUND
                val location = response.headers.location.shouldNotBeNull().toString()
                location shouldStartWith "http://localhost:8080/callback?code="
                location shouldContain "state=test-state"
            }
        }
    }

    describe("GET /oauth2/consent - 동의 정보 조회") {

        context("인증된 사용자가 third-party 클라이언트의 동의 정보 조회") {
            it("200 과 클라이언트 정보·요청 스코프·redirect_uri·state 를 돌려준다") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken, isFirstParty = false)
                val (userToken, _) = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>(
                    "/oauth2/consent?client_id=$clientId&redirect_uri=http://localhost:8080/callback&scope=openid profile email&state=c1",
                    userToken,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body.shouldNotBeNull()
                body["clientId"] shouldBe clientId
                body["clientName"].shouldNotBeNull()
                body["requestedScopes"] shouldBe listOf("openid", "profile", "email")
                body["redirectUri"] shouldBe "http://localhost:8080/callback"
                body["state"] shouldBe "c1"
            }
        }

        context("PKCE 를 요구하는 클라이언트") {
            it("code_challenge 를 넘기면 200, 빠지면 400 invalid_request") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken, isFirstParty = false, requirePkce = true)
                val (userToken, _) = createAndLoginUser()
                val base = "client_id=$clientId&redirect_uri=http://localhost:8080/callback&scope=openid"

                // when
                val withChallenge = consentGet(userToken, "$base&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256")
                val withoutChallenge = consentGet(userToken, base)

                // then
                withChallenge.statusCode shouldBe HttpStatus.OK
                withoutChallenge.statusCode shouldBe HttpStatus.BAD_REQUEST
                withoutChallenge.body.shouldNotBeNull() shouldContain "invalid_request"
            }
        }

        context("미지 client_id 로 동의 정보 조회") {
            it("401 invalid_client JSON, Location 없음") {
                // given
                val (userToken, _) = createAndLoginUser()

                // when
                val response = consentGet(userToken, "client_id=nope&redirect_uri=http://localhost:8080/callback&scope=openid")

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                response.headers.location shouldBe null
                response.body.shouldNotBeNull() shouldContain "invalid_client"
            }
        }

        context("인증 없이 동의 정보 조회") {
            it("401 Unauthorized를 반환한다") {
                // given
                val adminToken = getAdminToken()
                val client = createOAuth2Client(adminToken)

                val (clientId, _) = client
                val url = "/oauth2/consent?" +
                    "client_id=$clientId&" +
                    "redirect_uri=http://localhost:8080/callback&" +
                    "scope=openid profile"

                // when
                val response = restTemplate.getForEntity(url, String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("POST /oauth2/consent - 동의 처리") {

        context("approve — 등록 redirect_uri") {
            it("302 code → authorization_code 교환 200(access·refresh) → refresh_token grant 200") {
                // given
                val adminToken = getAdminToken()
                val (clientId, clientSecret) = createOAuth2Client(adminToken, isFirstParty = false)
                val (userToken, _) = createAndLoginUser()

                // when — 동의
                val consent = consentPost(userToken, consentForm(clientId, "http://localhost:8080/callback", approve = true))

                // then — 검증된 redirect_uri 로 code 를 붙여 302
                consent.statusCode shouldBe HttpStatus.FOUND
                val location = consent.headers.location.shouldNotBeNull().toString()
                location shouldStartWith "http://localhost:8080/callback?code="
                location shouldContain "state=consent-state"
                val code = location.substringAfter("code=").substringBefore("&")

                // when — code 교환
                val tokenResponse = restTemplate.postForm<Map<String, Any>>(
                    "/oauth2/token",
                    LinkedMultiValueMap<String, String>().apply {
                        add("grant_type", "authorization_code")
                        add("code", code)
                        add("redirect_uri", "http://localhost:8080/callback")
                        add("client_id", clientId)
                        add("client_secret", clientSecret)
                    },
                )

                // then
                tokenResponse.statusCode shouldBe HttpStatus.OK
                val tokens = tokenResponse.body.shouldNotBeNull()
                tokens["access_token"].shouldNotBeNull()
                val refreshToken = tokens["refresh_token"].shouldNotBeNull() as String

                // when — refresh grant
                val refreshed = restTemplate.postForm<Map<String, Any>>(
                    "/oauth2/token",
                    LinkedMultiValueMap<String, String>().apply {
                        add("grant_type", "refresh_token")
                        add("refresh_token", refreshToken)
                        add("client_id", clientId)
                        add("client_secret", clientSecret)
                    },
                )

                // then
                refreshed.statusCode shouldBe HttpStatus.OK
                refreshed.body.shouldNotBeNull()["access_token"].shouldNotBeNull()
            }
        }

        context("approve — 등록되지 않은 redirect_uri") {
            it("400 JSON, Location 없음, 코드 미발급 (오픈 리다이렉트·코드 유출 차단)") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken, isFirstParty = false)
                val (userToken, _) = createAndLoginUser()
                val before = codeCount(clientId)

                // when
                val response = consentPost(userToken, consentForm(clientId, "https://evil.example/cb", approve = true))

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.headers.location shouldBe null
                response.body.shouldNotBeNull() shouldContain "invalid_request"
                codeCount(clientId) shouldBe before
            }
        }

        context("deny — 등록 redirect_uri") {
            it("검증된 redirect_uri 로 error=access_denied 302") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken, isFirstParty = false)
                val (userToken, _) = createAndLoginUser()

                // when
                val response = consentPost(userToken, consentForm(clientId, "http://localhost:8080/callback", approve = false))

                // then
                response.statusCode shouldBe HttpStatus.FOUND
                val location = response.headers.location.shouldNotBeNull().toString()
                location shouldStartWith "http://localhost:8080/callback?error=access_denied"
                location shouldContain "state=consent-state"
            }
        }

        context("deny — 등록되지 않은 redirect_uri") {
            it("deny 도 검증 뒤다 — 400 JSON, Location 없음, 코드 미발급") {
                // given
                val adminToken = getAdminToken()
                val (clientId, _) = createOAuth2Client(adminToken, isFirstParty = false)
                val (userToken, _) = createAndLoginUser()
                val before = codeCount(clientId)

                // when
                val response = consentPost(userToken, consentForm(clientId, "https://evil.example/cb", approve = false))

                // then
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.headers.location shouldBe null
                codeCount(clientId) shouldBe before
            }
        }
    }
})