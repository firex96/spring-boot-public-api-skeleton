package com.rex.api.pub.skeleton.controller.oauth2

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.config.security.ClientCredentialsResolver
import com.rex.api.pub.skeleton.config.security.browser.AuthorizationRequestStore
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.fixture.OAuth2ClientFixture
import com.rex.api.pub.skeleton.model.oauth2.AuthorizeDecisionModel
import com.rex.api.pub.skeleton.model.oauth2.OAuth2ErrorResponseModel
import com.rex.api.pub.skeleton.model.oauth2.OidcTokenResponseModel
import com.rex.api.pub.skeleton.model.oauth2.TokenIntrospectionResponseModel
import com.rex.api.pub.skeleton.service.mapping.OAuth2AuthorizationMappingService
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * OAuth2AuthorizationController 통합 테스트.
 *
 * OAuth2 Authorization Server API (토큰 발급, 검증, 취소) 엔드포인트를 테스트합니다.
 * 컨트롤러는 `OAuth2AuthorizationMappingService` 만 주입받고 Model 을 응답한다 — 판정(`AuthorizeDecisionModel`)에 따른 리다이렉트와
 * `@ExceptionHandler` 의 상태 매핑이 이 테스트의 대상이다. JSON 필드명 단언은 DTO 시절과 글자 동일(응답 계약 무변경).
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.oauth2.OAuth2AuthorizationControllerTest"
 * ```
 *
 * 참고: 로그인·동의 화면은 `OAuth2BrowserControllerTest`, 브라우저 전체 왕복(세션 포함)은 `OAuth2AuthorizationE2ETest`.
 */
@WebMvcTest(controllers = [OAuth2AuthorizationController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class, AuthorizationRequestStore::class, ClientCredentialsResolver::class)
class OAuth2AuthorizationControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var oAuth2AuthorizationMappingService: OAuth2AuthorizationMappingService

    init {
        // @ExceptionHandler 가 부르는 오류 Model 변환 — MappingService 가 mock 이라 실제 매핑을 흉내낸다
        beforeTest {
            every { oAuth2AuthorizationMappingService.toErrorModel(any()) } answers {
                val e = firstArg<OAuth2Exception>()
                OAuth2ErrorResponseModel(error = e.error, errorDescription = e.errorDescription, errorUri = e.errorUri)
            }
        }

        // ============================================
        // Authorization Endpoint — RFC 6749 §4.1.2.1: client·redirect_uri 가 잘못되면 리다이렉트하지 않는다
        // ============================================
        Given("GET /oauth2/authorize - client·redirect_uri 검증") {
            When("client 검증이 invalid_client 로 실패하면") {
                every { oAuth2AuthorizationMappingService.authorize(any(), null, null) } throws
                    OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")

                Then("401 JSON 으로 끝나고 Location 헤더가 없다 (요청 redirect_uri 로 리다이렉트하지 않는다)") {
                    mockMvc.get("/oauth2/authorize") {
                        param("response_type", "code")
                        param("client_id", "nope")
                        param("redirect_uri", "https://evil.example/cb")
                    }.andExpect {
                        status { isUnauthorized() }
                        header { doesNotExist("Location") }
                        jsonPath("$.error") { value("invalid_client") }
                    }
                }
            }

            When("client 는 유효하지만 scope 가 잘못되면") {
                every { oAuth2AuthorizationMappingService.authorize(any(), null, null) } returns
                    OAuth2ClientFixture.authorizeDecision(
                        AuthorizeNextStep.ERROR,
                        error = OAuth2AuthorizationBizService.ERROR_INVALID_SCOPE,
                        errorDescription = "지원하지 않는 scope입니다: admin",
                    )

                Then("검증된 redirect_uri 로 error=invalid_scope 리다이렉트한다") {
                    mockMvc.get("/oauth2/authorize") {
                        param("response_type", "code")
                        param("client_id", "web-app")
                        param("redirect_uri", "https://example.com/callback")
                        param("scope", "admin")
                        param("state", "xyz")
                    }.andExpect {
                        status { isFound() }
                        header {
                            string(
                                "Location",
                                "https://example.com/callback?error=invalid_scope&error_description=%EC%A7%80%EC%9B%90%ED%95%98%EC%A7%80+%EC%95%8A%EB%8A%94+scope%EC%9E%85%EB%8B%88%EB%8B%A4%3A+admin&state=xyz",
                            )
                        }
                    }
                }
            }

            When("로그인하지 않은 사용자면") {
                every { oAuth2AuthorizationMappingService.authorize(any(), null, null) } returns OAuth2ClientFixture.authorizeDecision(AuthorizeNextStep.LOGIN_REQUIRED)

                Then("로그인 화면으로 302 하고, 돌아올 authorize URL 은 쿼리가 아니라 세션에 둔다") {
                    val result =
                        mockMvc.get("/oauth2/authorize") {
                            param("response_type", "code")
                            param("client_id", "web-app")
                            param("redirect_uri", "https://example.com/callback")
                        }.andExpect {
                            status { isFound() }
                            header { string("Location", "/oauth2/login") }
                        }.andReturn()

                    result.request.getSession(false).shouldNotBeNull().getAttribute(AuthorizationRequestStore.ATTRIBUTE) shouldBe
                        "/oauth2/authorize?response_type=code&client_id=web-app&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"
                }
            }
        }

        // ============================================
        // 토큰 발급 - Authorization Code
        // ============================================
        Given("POST /oauth2/token - Authorization Code Grant") {
            When("유효한 authorization_code로 토큰 요청") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                every {
                    oAuth2AuthorizationMappingService.token(
                        grantType = "authorization_code",
                        code = "valid-auth-code",
                        redirectUri = "https://example.com/callback",
                        clientId = "web-app",
                        clientSecret = "client-secret",
                        refreshToken = null,
                        scope = null,
                        codeVerifier = null,
                    )
                } returns
                    OidcTokenResponseModel(
                        accessToken = "access-token-12345",
                        tokenType = "Bearer",
                        expiresIn = 3600,
                        refreshToken = "refresh-token-67890",
                        scope = "openid profile email",
                        idToken = "id-token-abcde",
                    )

                Then("200 OK와 토큰 반환") {
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("grant_type", "authorization_code")
                        param("code", "valid-auth-code")
                        param("redirect_uri", "https://example.com/callback")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.access_token") { value("access-token-12345") }
                        jsonPath("$.token_type") { value("Bearer") }
                        jsonPath("$.expires_in") { value(3600) }
                        jsonPath("$.refresh_token") { value("refresh-token-67890") }
                        jsonPath("$.id_token") { value("id-token-abcde") }
                    }
                }
            }

            When("PKCE code_verifier와 함께 토큰 요청") {
                every { oAuth2AuthorizationMappingService.authenticateClient("mobile-app", "mobile-secret") } returns "mobile-app"
                every {
                    oAuth2AuthorizationMappingService.token(
                        grantType = "authorization_code",
                        code = "auth-code-pkce",
                        redirectUri = "myapp://callback",
                        clientId = "mobile-app",
                        clientSecret = "mobile-secret",
                        refreshToken = null,
                        scope = null,
                        codeVerifier = "verifier-12345",
                    )
                } returns
                    OidcTokenResponseModel(
                        accessToken = "access-token-pkce",
                        tokenType = "Bearer",
                        expiresIn = 3600,
                        refreshToken = "refresh-token-pkce",
                        scope = "openid profile",
                        idToken = null,
                    )

                Then("200 OK와 토큰 반환") {
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("grant_type", "authorization_code")
                        param("code", "auth-code-pkce")
                        param("redirect_uri", "myapp://callback")
                        param("client_id", "mobile-app")
                        param("client_secret", "mobile-secret")
                        param("code_verifier", "verifier-12345")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.access_token") { value("access-token-pkce") }
                    }
                }
            }
        }

        // ============================================
        // 토큰 발급 - Refresh Token
        // ============================================
        Given("POST /oauth2/token - Refresh Token Grant") {
            When("유효한 refresh_token으로 토큰 갱신") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                every {
                    oAuth2AuthorizationMappingService.token(
                        grantType = "refresh_token",
                        code = null,
                        redirectUri = null,
                        clientId = "web-app",
                        clientSecret = "client-secret",
                        refreshToken = "valid-refresh-token",
                        scope = null,
                        codeVerifier = null,
                    )
                } returns
                    OidcTokenResponseModel(
                        accessToken = "new-access-token",
                        tokenType = "Bearer",
                        expiresIn = 3600,
                        refreshToken = null,
                        scope = "openid profile email",
                        idToken = null,
                    )

                Then("200 OK와 새 액세스 토큰 반환") {
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("grant_type", "refresh_token")
                        param("refresh_token", "valid-refresh-token")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.access_token") { value("new-access-token") }
                        jsonPath("$.token_type") { value("Bearer") }
                        jsonPath("$.expires_in") { value(3600) }
                    }
                }
            }
        }

        // ============================================
        // 토큰 발급 - Client Credentials
        // ============================================
        Given("POST /oauth2/token - Client Credentials Grant") {
            When("유효한 클라이언트 자격 증명으로 토큰 요청") {
                every { oAuth2AuthorizationMappingService.authenticateClient("machine-client", "machine-secret") } returns "machine-client"
                every {
                    oAuth2AuthorizationMappingService.token(
                        grantType = "client_credentials",
                        code = null,
                        redirectUri = null,
                        clientId = "machine-client",
                        clientSecret = "machine-secret",
                        refreshToken = null,
                        scope = "read write",
                        codeVerifier = null,
                    )
                } returns
                    OidcTokenResponseModel(
                        accessToken = "machine-access-token",
                        tokenType = "Bearer",
                        expiresIn = 3600,
                        refreshToken = null,
                        scope = "read write",
                        idToken = null,
                    )

                Then("200 OK와 액세스 토큰 반환 (refresh_token 없음)") {
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("grant_type", "client_credentials")
                        param("client_id", "machine-client")
                        param("client_secret", "machine-secret")
                        param("scope", "read write")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.access_token") { value("machine-access-token") }
                        jsonPath("$.token_type") { value("Bearer") }
                        jsonPath("$.refresh_token") { doesNotExist() }
                    }
                }
            }
        }

        // ============================================
        // 토큰 발급 - 에러 케이스
        // ============================================
        Given("POST /oauth2/token - 에러 케이스") {
            When("지원하지 않는 grant_type 요청") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                every {
                    oAuth2AuthorizationMappingService.token(any(), any(), any(), any(), any(), any(), any(), any())
                } throws OAuth2Exception("unsupported_grant_type", "지원하지 않는 grant_type입니다")

                Then("400 Bad Request와 에러 응답 반환") {
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("grant_type", "password")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isBadRequest() }
                        jsonPath("$.error") { value("unsupported_grant_type") }
                    }
                }
            }

            When("유효하지 않은 클라이언트 인증") {
                every { oAuth2AuthorizationMappingService.authenticateClient("invalid-client", "wrong-secret") } throws
                    OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "클라이언트 인증에 실패했습니다")

                Then("401 Unauthorized와 에러 응답 반환 — 토큰 발급은 호출되지 않는다") {
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("grant_type", "client_credentials")
                        param("client_id", "invalid-client")
                        param("client_secret", "wrong-secret")
                    }.andExpect {
                        status { isUnauthorized() }
                        header { string("WWW-Authenticate", "Basic realm=\"oauth2\"") }
                        jsonPath("$.error") { value("invalid_client") }
                    }
                    verify(exactly = 0) { oAuth2AuthorizationMappingService.token(any(), any(), any(), any(), any(), any(), any(), any()) }
                }
            }

            When("client_secret_basic(Authorization: Basic)로 인증하면") {
                every { oAuth2AuthorizationMappingService.authenticateClient("machine-client", "s3:cr/t") } returns "machine-client"
                every { oAuth2AuthorizationMappingService.token(any(), any(), any(), "machine-client", "s3:cr/t", any(), any(), any()) } returns
                    OidcTokenResponseModel(accessToken = "basic-token", tokenType = "Bearer", expiresIn = 3600, refreshToken = null, scope = null, idToken = null)

                Then("헤더의 id·secret(폼 URL 디코드)으로 인증한다") {
                    val basic = java.util.Base64.getEncoder().encodeToString("machine-client:s3%3Acr%2Ft".toByteArray())
                    mockMvc.post("/oauth2/token") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        header("Authorization", "Basic $basic")
                        param("grant_type", "client_credentials")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.access_token") { value("basic-token") }
                    }
                }
            }
        }

        // ============================================
        // 토큰 검증 (Introspection)
        // ============================================
        Given("POST /oauth2/introspect - 토큰 검증") {
            When("유효한 액세스 토큰 검증") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                every { oAuth2AuthorizationMappingService.introspect("valid-access-token", "web-app") } returns
                    TokenIntrospectionResponseModel(
                        active = true,
                        scope = "openid profile email",
                        clientId = "web-app",
                        username = "testuser",
                        tokenType = "Bearer",
                        exp = 1700000000L,
                        iat = 1699996400L,
                        nbf = null,
                        sub = "12345",
                        aud = "web-app",
                        iss = "https://auth.example.com",
                        jti = "token-id-123",
                    )

                Then("200 OK와 토큰 정보 반환") {
                    mockMvc.post("/oauth2/introspect") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("token", "valid-access-token")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.active") { value(true) }
                        jsonPath("$.scope") { value("openid profile email") }
                        jsonPath("$.clientId") { value("web-app") }
                        jsonPath("$.username") { value("testuser") }
                    }
                }
            }

            When("만료된 또는 취소된 토큰 검증") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                every { oAuth2AuthorizationMappingService.introspect("expired-token", "web-app") } returns
                    TokenIntrospectionResponseModel(
                        active = false,
                        scope = null,
                        clientId = null,
                        username = null,
                        tokenType = null,
                        exp = null,
                        iat = null,
                        nbf = null,
                        sub = null,
                        aud = null,
                        iss = null,
                        jti = null,
                    )

                Then("200 OK와 active=false 반환") {
                    mockMvc.post("/oauth2/introspect") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("token", "expired-token")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.active") { value(false) }
                    }
                }
            }
        }

        // ============================================
        // 토큰 취소 (Revocation)
        // ============================================
        Given("POST /oauth2/revoke - 토큰 취소") {
            When("액세스 토큰 취소 요청") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                justRun { oAuth2AuthorizationMappingService.revoke("token-to-revoke", "web-app") }

                Then("200 OK 반환") {
                    mockMvc.post("/oauth2/revoke") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("token", "token-to-revoke")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isOk() }
                    }
                    verify(exactly = 1) { oAuth2AuthorizationMappingService.revoke("token-to-revoke", "web-app") }
                }
            }

            When("refresh_token 취소 요청") {
                every { oAuth2AuthorizationMappingService.authenticateClient("web-app", "client-secret") } returns "web-app"
                justRun { oAuth2AuthorizationMappingService.revoke("refresh-token-to-revoke", "web-app") }

                Then("200 OK 반환") {
                    mockMvc.post("/oauth2/revoke") {
                        contentType = MediaType.APPLICATION_FORM_URLENCODED
                        param("token", "refresh-token-to-revoke")
                        param("token_type_hint", "refresh_token")
                        param("client_id", "web-app")
                        param("client_secret", "client-secret")
                    }.andExpect {
                        status { isOk() }
                    }
                }
            }
        }
    }
}