package com.rex.api.pub.skeleton.oauth2

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.config.E2ETestDataSetup
import com.rex.api.pub.skeleton.persistence.jpa.repository.auth.OAuth2AuthorizationCodeRepository
import com.rex.api.pub.skeleton.support.BrowserSession
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.createOAuth2TestClient
import com.rex.api.pub.skeleton.support.exchangeAuthorizationCode
import com.rex.api.pub.skeleton.support.postForm
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
import org.springframework.util.LinkedMultiValueMap
import java.net.URI
import java.net.http.HttpResponse

/**
 * OAuth2 Authorization API E2E 테스트.
 *
 * OAuth2 인증 서버 API를 실제 HTTP 요청으로 테스트합니다.
 * - Authorization Code Flow — 브라우저 세션(SKELETON_SESSION) 흐름: authorize → 로그인 화면 → 동의 화면 → code
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

    // 브라우저 하나 = BrowserSession 하나 — 세션 쿠키를 유지하고 리다이렉트를 따르지 않는다(302·Location 을 단언한다)
    fun newBrowser() = BrowserSession(restTemplate.rootUri.orEmpty())

    /** 서블릿 컨테이너는 Location 을 절대 URL 로 내보낸다 — 이 서버의 출처를 떼고 경로·쿼리로 다룬다. */
    fun HttpResponse<*>.location(): String = newBrowser().location(this)

    /** authorize → (로그인 필요면) 로그인 화면 → 로그인 → 원래 authorize. 마지막 authorize 응답을 돌려준다(302 동의 또는 code). */
    fun authorizeViaBrowser(jar: BrowserSession, query: String, loginId: String): HttpResponse<String> {
        val first = jar.get("/oauth2/authorize?$query")
        if (first.location() != "/oauth2/login") return first
        val login = jar.postForm("/oauth2/login", mapOf("loginId" to loginId, "password" to E2ETestDataSetup.DEFAULT_TEST_PASSWORD, "_csrf" to jar.csrf()))
        login.statusCode() shouldBe 302
        login.location() shouldStartWith "/oauth2/authorize?"
        return jar.get(login.location())
    }

    /** 동의 폼 제출 — 화면의 hidden 필드와 같은 이름. CSRF 는 이 세션의 값. */
    fun submitConsent(jar: BrowserSession, form: Map<String, String>, approve: Boolean, withCsrf: Boolean = true): HttpResponse<String> =
        jar.postForm("/oauth2/consent", form + ("approve" to approve.toString()) + (if (withCsrf) mapOf("_csrf" to jar.csrf()) else emptyMap()))

    fun consentForm(clientId: String, redirectUri: String, state: String = "consent-state", scope: String = "openid profile email") =
        mapOf("client_id" to clientId, "redirect_uri" to redirectUri, "scope" to scope, "state" to state)

    fun authorizeQuery(clientId: String, state: String = "consent-state", scope: String = "openid profile email", redirectUri: String = "http://localhost:8080/callback") =
        "response_type=code&client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}&scope=${enc(scope)}&state=${enc(state)}"

    /** 로그인한 브라우저 세션 — 아무 authorize 없이 로그인만. */
    fun loggedInBrowser(loginId: String): BrowserSession {
        val jar = newBrowser()
        jar.postForm("/oauth2/login", mapOf("loginId" to loginId, "password" to E2ETestDataSetup.DEFAULT_TEST_PASSWORD, "_csrf" to jar.csrf())).statusCode() shouldBe 302
        return jar
    }

    fun codeCount(clientId: String) = oAuth2AuthorizationCodeRepository.findAll().count { it.clientId == clientId }

    // 일반 사용자 생성 및 로그인
    // (accessToken, loginId) — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): Pair<String, String> =
        restTemplate.createAndLoginUser(resources, "e2e_oauth2_").let { it.accessToken to it.loginId }

    // OAuth2 클라이언트 생성 — 공용 헬퍼(관리자 API, 정리 등록). 이 스펙은 offline_access 없는 기본 scope 를 쓴다
    fun createOAuth2Client(isFirstParty: Boolean = true, requirePkce: Boolean = false): Pair<String, String> =
        restTemplate.createOAuth2TestClient(resources, scopes = listOf("openid", "profile", "email"), isFirstParty = isFirstParty, requirePkce = requirePkce)

    describe("POST /oauth2/token - 토큰 발급") {

        context("client_credentials grant type") {
            it("Access Token을 반환한다") {
                // given
                val client = createOAuth2Client()

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
                val client = createOAuth2Client()

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
                val (clientId, clientSecret) = createOAuth2Client()
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

    describe("POST /oauth2/token - scope 는 승인·등록 범위를 넘을 수 없다") {

        fun refresh(clientId: String, clientSecret: String, refreshToken: String, scope: String) =
            newBrowser().postForm(
                "/oauth2/token",
                mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to clientId, "client_secret" to clientSecret, "scope" to scope),
            )

        context("openid 만 승인받은 refresh_token 으로 profile email 을 요청") {
            it("400 invalid_scope — 승인하지 않은 범위로 넓힐 수 없다. 승인 범위 안의 축소·유지는 200") {
                val (clientId, clientSecret) = createOAuth2Client(isFirstParty = true)
                val (_, loginId) = createAndLoginUser()
                val code = authorizeViaBrowser(newBrowser(), authorizeQuery(clientId, scope = "openid"), loginId).location().substringAfter("code=").substringBefore("&")
                val refreshToken = restTemplate.exchangeAuthorizationCode(clientId, clientSecret, code)["refresh_token"].shouldNotBeNull() as String

                val widened = refresh(clientId, clientSecret, refreshToken, "openid profile email")
                widened.statusCode() shouldBe 400
                widened.body() shouldContain "invalid_scope"

                refresh(clientId, clientSecret, refreshToken, "openid").statusCode() shouldBe 200
            }
        }

        context("client_credentials 로 등록하지 않은 scope 를 요청") {
            it("400 invalid_scope") {
                val (clientId, clientSecret) = createOAuth2Client()

                val response =
                    newBrowser().postForm(
                        "/oauth2/token",
                        mapOf("grant_type" to "client_credentials", "client_id" to clientId, "client_secret" to clientSecret, "scope" to "openid admin"),
                    )

                response.statusCode() shouldBe 400
                response.body() shouldContain "invalid_scope"
            }
        }
    }

    describe("POST /oauth2/token - 클라이언트 인증 방식 (discovery 의 token_endpoint_auth_methods_supported)") {

        fun tokenRequest(form: Map<String, String>, basic: Pair<String, String>? = null): HttpResponse<String> = newBrowser().postForm("/oauth2/token", form, basic)

        context("client_secret_basic") {
            it("Authorization: Basic 으로 client_credentials 를 발급받는다") {
                val (clientId, clientSecret) = createOAuth2Client()

                val response = tokenRequest(mapOf("grant_type" to "client_credentials"), basic = clientId to clientSecret)

                response.statusCode() shouldBe 200
                response.body() shouldContain "access_token"
            }
        }

        context("client_secret_post") {
            it("폼 client_id·client_secret 으로 발급받는다") {
                val (clientId, clientSecret) = createOAuth2Client()

                val response = tokenRequest(mapOf("grant_type" to "client_credentials", "client_id" to clientId, "client_secret" to clientSecret))

                response.statusCode() shouldBe 200
            }
        }

        context("시크릿 없이 client_id 만") {
            it("401 invalid_client 와 WWW-Authenticate — 시크릿 없는 클라이언트 인증은 없다") {
                val (clientId, _) = createOAuth2Client()

                val response = tokenRequest(mapOf("grant_type" to "client_credentials", "client_id" to clientId))

                response.statusCode() shouldBe 401
                response.body() shouldContain "invalid_client"
                response.headers().firstValue("WWW-Authenticate").orElse("") shouldContain "Basic"
            }
        }

        context("두 방식을 함께") {
            it("400 invalid_request") {
                val (clientId, clientSecret) = createOAuth2Client()

                val response =
                    tokenRequest(
                        mapOf("grant_type" to "client_credentials", "client_id" to clientId, "client_secret" to clientSecret),
                        basic = clientId to clientSecret,
                    )

                response.statusCode() shouldBe 400
                response.body() shouldContain "invalid_request"
            }
        }
    }

    describe("POST /oauth2/introspect - 토큰 검증") {

        context("유효한 토큰 검증") {
            it("active: true를 반환한다") {
                // given
                val client = createOAuth2Client()

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
                val client = createOAuth2Client()

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
                val client = createOAuth2Client()

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
                val client = createOAuth2Client()

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

        context("로그인하지 않은 브라우저") {
            it("로그인 화면으로 302 하고, 돌아올 곳을 쿼리로 넘기지 않는다") {
                val (clientId, _) = createOAuth2Client()

                val response = newBrowser().get("/oauth2/authorize?${authorizeQuery(clientId, state = "test-state")}")

                response.statusCode() shouldBe 302
                response.location() shouldBe "/oauth2/login"
            }
        }

        context("Bearer 만 있고 브라우저 세션이 없는 요청") {
            it("로그인 화면으로 간다 — authorize 는 브라우저 세션으로만 인증한다(JWT 필터는 브라우저 체인에서 돌지 않는다)") {
                val (clientId, _) = createOAuth2Client()
                val (userToken, _) = createAndLoginUser()

                val response = newBrowser().get("/oauth2/authorize?${authorizeQuery(clientId)}", bearer = userToken)

                response.location() shouldBe "/oauth2/login"
            }
        }

        context("잘못된 client_id로 요청") {
            it("401 JSON 으로 끝나고 리다이렉트하지 않는다 (RFC 6749 §4.1.2.1 — client 가 잘못되면 redirect_uri 를 신뢰할 수 없다)") {
                val response = newBrowser().get("/oauth2/authorize?${authorizeQuery("invalid-client-id", scope = "openid profile")}")

                response.statusCode() shouldBe 401
                response.headers().firstValue("Location").isPresent shouldBe false
                response.body() shouldContain "invalid_client"
            }
        }

        context("등록되지 않은 redirect_uri 로 요청") {
            it("400 JSON 으로 끝나고 그 URI 로 리다이렉트하지 않는다") {
                val (clientId, _) = createOAuth2Client()

                val response = newBrowser().get("/oauth2/authorize?${authorizeQuery(clientId, redirectUri = "https://evil.example/cb", scope = "openid")}")

                response.statusCode() shouldBe 400
                response.headers().firstValue("Location").isPresent shouldBe false
                response.body() shouldContain "invalid_request"
            }
        }

        context("등록 client 가 지원하지 않는 scope 로 요청") {
            it("검증된 redirect_uri 로 error=invalid_scope 리다이렉트한다") {
                val (clientId, _) = createOAuth2Client()

                val response = newBrowser().get("/oauth2/authorize?${authorizeQuery(clientId, state = "scope-state", scope = "admin")}")

                response.statusCode() shouldBe 302
                response.location() shouldStartWith "http://localhost:8080/callback?error=invalid_scope"
                response.location() shouldContain "state=scope-state"
            }
        }

        context("first-party 앱 — 브라우저 로그인 뒤") {
            it("동의 없이 Authorization Code 를 발급해 redirect_uri 로 보낸다") {
                val (clientId, _) = createOAuth2Client(isFirstParty = true)
                val (_, loginId) = createAndLoginUser()

                val response = authorizeViaBrowser(newBrowser(), authorizeQuery(clientId, state = "test-state", scope = "openid profile"), loginId)

                response.statusCode() shouldBe 302
                response.location() shouldStartWith "http://localhost:8080/callback?code="
                response.location() shouldContain "state=test-state"
            }
        }
    }

    describe("브라우저 인가 흐름 — 로그인·동의 (third-party)") {

        context("authorize → 로그인 → 동의 화면 → 승인") {
            it("302 code → authorization_code 교환 200(access·refresh) → refresh_token grant 200") {
                // given
                val (clientId, clientSecret) = createOAuth2Client(isFirstParty = false)
                val (_, loginId) = createAndLoginUser()
                val jar = newBrowser()

                // when — authorize → 로그인 → 원래 authorize → 동의 화면으로
                val authorize = authorizeViaBrowser(jar, authorizeQuery(clientId), loginId)
                authorize.statusCode() shouldBe 302
                authorize.location() shouldStartWith "/oauth2/consent?"

                // then — 동의 화면은 HTML 이고 클라이언트·scope 를 보여 준다
                val consentPage = jar.get(authorize.location())
                consentPage.statusCode() shouldBe 200
                consentPage.headers().firstValue("Content-Type").orElse("") shouldContain "text/html"
                consentPage.body() shouldContain "E2E OAuth2 Client"
                consentPage.body() shouldContain "profile"

                // when — 승인
                val consent = submitConsent(jar, consentForm(clientId, "http://localhost:8080/callback"), approve = true)

                // then — 검증된 redirect_uri 로 code·state
                consent.statusCode() shouldBe 302
                consent.location() shouldStartWith "http://localhost:8080/callback?code="
                consent.location() shouldContain "state=consent-state"
                val code = consent.location().substringAfter("code=").substringBefore("&")

                // when — code 교환
                val refreshToken = restTemplate.exchangeAuthorizationCode(clientId, clientSecret, code)["refresh_token"].shouldNotBeNull() as String

                // when — refresh_token grant
                val refreshed = restTemplate.postForm<Map<String, Any>>(
                    "/oauth2/token",
                    LinkedMultiValueMap<String, String>().apply {
                        add("grant_type", "refresh_token")
                        add("refresh_token", refreshToken)
                        add("client_id", clientId)
                        add("client_secret", clientSecret)
                    },
                )
                refreshed.statusCode shouldBe HttpStatus.OK
                refreshed.body.shouldNotBeNull()["access_token"].shouldNotBeNull()
            }
        }

        context("state·nonce 가 \"false\"·\"off\" 같은 문자열") {
            it("렌더링된 동의 폼의 hidden 값을 그대로 제출하면 state 가 돌아오고 id_token nonce 가 유지된다") {
                val (clientId, clientSecret) = createOAuth2Client(isFirstParty = false)
                val (_, loginId) = createAndLoginUser()
                val jar = newBrowser()
                val authorize = authorizeViaBrowser(jar, authorizeQuery(clientId, state = "false") + "&nonce=off", loginId)

                // 화면이 실제로 렌더링한 hidden 필드만 모아 제출한다 — 테스트가 폼 값을 지어내지 않는다
                val page = jar.get(authorize.location()).body()
                val hidden = Regex("""<input type="hidden" name="([a-z_]+)" value="([^"]*)"""").findAll(page).associate { it.groupValues[1] to it.groupValues[2] }
                hidden["state"] shouldBe "false"
                hidden["nonce"] shouldBe "off"
                val consent = jar.postForm("/oauth2/consent", hidden + ("approve" to "true"))

                consent.location() shouldContain "state=false"
                val code = consent.location().substringAfter("code=").substringBefore("&")
                val idToken = restTemplate.exchangeAuthorizationCode(clientId, clientSecret, code)["id_token"].shouldNotBeNull() as String
                String(java.util.Base64.getUrlDecoder().decode(idToken.split(".")[1]), Charsets.UTF_8) shouldContain "\"nonce\":\"off\""
            }
        }

        context("동의 거부") {
            it("검증된 redirect_uri 로 error=access_denied·state 302") {
                val (clientId, _) = createOAuth2Client(isFirstParty = false)
                val (_, loginId) = createAndLoginUser()
                val jar = newBrowser()
                authorizeViaBrowser(jar, authorizeQuery(clientId), loginId)

                val response = submitConsent(jar, consentForm(clientId, "http://localhost:8080/callback"), approve = false)

                response.statusCode() shouldBe 302
                response.location() shouldStartWith "http://localhost:8080/callback?error=access_denied"
                response.location() shouldContain "state=consent-state"
            }
        }

        context("CSRF 토큰 없이 동의 제출") {
            it("403 ErrorResponseModel — 코드를 발급하지 않는다") {
                val (clientId, _) = createOAuth2Client(isFirstParty = false)
                val (_, loginId) = createAndLoginUser()
                val jar = loggedInBrowser(loginId)
                val before = codeCount(clientId)

                val response = submitConsent(jar, consentForm(clientId, "http://localhost:8080/callback"), approve = true, withCsrf = false)

                response.statusCode() shouldBe 403
                response.body() shouldContain "FORBIDDEN_ERROR"
                codeCount(clientId) shouldBe before
            }
        }

        context("로그인하지 않은 브라우저의 동의 화면") {
            it("로그인 화면으로 302") {
                val (clientId, _) = createOAuth2Client(isFirstParty = false)

                val response = newBrowser().get("/oauth2/consent?client_id=$clientId&redirect_uri=http://localhost:8080/callback&scope=openid")

                response.statusCode() shouldBe 302
                response.location() shouldBe "/oauth2/login"
            }
        }

        context("사용자 세션을 폐기하면(전체 로그아웃)") {
            it("같은 브라우저 세션의 다음 authorize 는 다시 로그인 화면으로 간다") {
                val (clientId, _) = createOAuth2Client(isFirstParty = true)
                val (userToken, loginId) = createAndLoginUser()
                val jar = newBrowser()
                authorizeViaBrowser(jar, authorizeQuery(clientId), loginId).location() shouldStartWith "http://localhost:8080/callback?code="

                // when — Bearer 세션을 뺀 이 사용자의 모든 세션(브라우저 로그인 세션 포함) 폐기
                restTemplate.exchange(
                    "/users/me/sessions",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(userToken) }),
                    String::class.java,
                ).statusCode.is2xxSuccessful shouldBe true

                // then
                jar.get("/oauth2/authorize?${authorizeQuery(clientId)}").location() shouldBe "/oauth2/login"
            }
        }

        context("브라우저 세션은 사용자 A, Bearer 는 사용자 B") {
            it("코드는 A 로 발급된다 — Bearer 는 이 경로에서 읽히지 않는다") {
                val (clientId, _) = createOAuth2Client(isFirstParty = true)
                val userA = restTemplate.createAndLoginUser(resources, "e2e_oauth2_")
                val userB = restTemplate.createAndLoginUser(resources, "e2e_oauth2_")
                val jar = loggedInBrowser(userA.loginId)

                val response = jar.get("/oauth2/authorize?${authorizeQuery(clientId, scope = "openid")}", bearer = userB.accessToken)

                val code = response.location().substringAfter("code=").substringBefore("&")
                oAuth2AuthorizationCodeRepository.findAll().single { it.code == code }.userId shouldBe userA.userId
            }
        }
    }

    describe("동의 화면·동의 제출의 비리다이렉트 오류 — HTML 오류 화면, Location 없음, 코드 미발급") {

        context("PKCE 를 요구하는 클라이언트의 동의 화면") {
            it("code_challenge 를 넘기면 200, 빠지면 400 invalid_request") {
                val (clientId, _) = createOAuth2Client(isFirstParty = false, requirePkce = true)
                val (_, loginId) = createAndLoginUser()
                val jar = loggedInBrowser(loginId)
                val base = "client_id=$clientId&redirect_uri=http://localhost:8080/callback&scope=openid"

                val withChallenge = jar.get("/oauth2/consent?$base&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256")
                val withoutChallenge = jar.get("/oauth2/consent?$base")

                withChallenge.statusCode() shouldBe 200
                withoutChallenge.statusCode() shouldBe 400
                withoutChallenge.body() shouldContain "invalid_request"
            }
        }

        mapOf(
            "approve — 등록되지 않은 redirect_uri" to Triple(true, "https://evil.example/cb", 400),
            "deny — 등록되지 않은 redirect_uri" to Triple(false, "https://evil.example/cb", 400),
        ).forEach { (name, case) ->
            context(name) {
                it("${case.third} HTML, Location 없음, 코드 미발급 (오픈 리다이렉트·코드 유출 차단)") {
                    val (clientId, _) = createOAuth2Client(isFirstParty = false)
                    val (_, loginId) = createAndLoginUser()
                    val jar = loggedInBrowser(loginId)
                    val before = codeCount(clientId)

                    val response = submitConsent(jar, consentForm(clientId, case.second), approve = case.first)

                    response.statusCode() shouldBe case.third
                    response.headers().firstValue("Location").isPresent shouldBe false
                    response.headers().firstValue("Content-Type").orElse("") shouldContain "text/html"
                    response.body() shouldContain "invalid_request"
                    codeCount(clientId) shouldBe before
                }
            }
        }

        context("미지 client_id 로 동의 제출") {
            it("401 invalid_client HTML, Location 없음") {
                val (_, loginId) = createAndLoginUser()
                val jar = loggedInBrowser(loginId)

                val response = submitConsent(jar, consentForm("nope", "http://localhost:8080/callback"), approve = true)

                response.statusCode() shouldBe 401
                response.headers().firstValue("Location").isPresent shouldBe false
                response.body() shouldContain "invalid_client"
            }
        }

        context("허용되지 않은 scope·잘못된 code_challenge_method 로 동의 제출") {
            it("400 HTML, Location 없음, 코드 미발급") {
                val (clientId, _) = createOAuth2Client(isFirstParty = false)
                val (_, loginId) = createAndLoginUser()
                val jar = loggedInBrowser(loginId)
                val before = codeCount(clientId)

                val badScope = submitConsent(jar, consentForm(clientId, "http://localhost:8080/callback", scope = "admin"), approve = true)
                val badMethod =
                    submitConsent(
                        jar,
                        consentForm(clientId, "http://localhost:8080/callback") + mapOf("code_challenge" to "abc", "code_challenge_method" to "S512"),
                        approve = true,
                    )

                listOf(badScope, badMethod).forEach { response ->
                    response.statusCode() shouldBe 400
                    response.headers().firstValue("Location").isPresent shouldBe false
                }
                badScope.body() shouldContain "invalid_scope"
                badMethod.body() shouldContain "invalid_request"
                codeCount(clientId) shouldBe before
            }
        }
    }
})

private fun enc(value: String): String = BrowserSession.encode(value)