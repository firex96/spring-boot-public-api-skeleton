package com.rex.api.pub.skeleton.controller.oauth2

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.config.security.AuthenticatedUser
import com.rex.api.pub.skeleton.config.security.browser.AuthorizationRequestStore
import com.rex.api.pub.skeleton.config.security.browser.BrowserSessionAuthenticator
import com.rex.api.pub.skeleton.config.security.browser.MfaPendingTokenStore
import com.rex.api.pub.skeleton.dto.oauth2.AuthorizationRequestDto
import com.rex.api.pub.skeleton.enums.oauth2.AuthorizeNextStep
import com.rex.api.pub.skeleton.exception.auth.AuthenticationException
import com.rex.api.pub.skeleton.exception.oauth2.OAuth2Exception
import com.rex.api.pub.skeleton.fixture.OAuth2ClientFixture
import com.rex.api.pub.skeleton.model.auth.BrowserLoginOutcomeModel
import com.rex.api.pub.skeleton.model.auth.BrowserPrincipalModel
import com.rex.api.pub.skeleton.model.oauth2.AuthorizationConsentModel
import com.rex.api.pub.skeleton.service.mapping.AuthMappingService
import com.rex.api.pub.skeleton.service.mapping.OAuth2AuthorizationMappingService
import com.rex.api.pub.skeleton.service.oauth2.OAuth2AuthorizationBizService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import com.rex.api.pub.skeleton.util.ClientIpResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * OAuth2BrowserController — 인가 서버의 로그인·동의 화면.
 *
 * 뷰 이름·모델·리다이렉트와 검증 실패의 HTML 오류 화면(Location 없음)이 대상이다. 보안 필터는 끈다 —
 * 세션·CSRF·브라우저 왕복은 `OAuth2AuthorizationE2ETest` 가 실제 체인으로 본다.
 *
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.oauth2.OAuth2BrowserControllerTest"
 * ```
 */
@WebMvcTest(controllers = [OAuth2BrowserController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class, AuthorizationRequestStore::class)
class OAuth2BrowserControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var oAuth2AuthorizationMappingService: OAuth2AuthorizationMappingService

    @MockkBean
    lateinit var authMappingService: AuthMappingService

    @MockkBean
    lateinit var clientIpResolver: ClientIpResolver

    @MockkBean
    lateinit var browserSessionAuthenticator: BrowserSessionAuthenticator

    @MockkBean(relaxUnitFun = true)
    lateinit var mfaPendingTokenStore: MfaPendingTokenStore

    init {
        val principal = AuthenticatedUser(userId = 7L, sessionId = "session-7", groups = emptyList(), permissions = emptyList())
        val browserPrincipal = BrowserPrincipalModel(userId = 7L, sessionId = "session-7", groups = emptyList(), permissions = emptyList())
        val pendingAuthorize = "/oauth2/authorize?response_type=code&client_id=web-app&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"

        beforeTest { every { clientIpResolver.resolve(any()) } returns "203.0.113.10" }

        Given("GET /oauth2/login") {
            Then("로그인 화면 뷰") {
                mockMvc.get("/oauth2/login").andExpect {
                    status { isOk() }
                    view { name("oauth2/login") }
                }
            }
        }

        Given("POST /oauth2/login") {
            When("로그인에 성공하고 기다리던 authorize 요청이 세션에 있으면") {
                every { authMappingService.loginForBrowser(any(), "203.0.113.10") } returns BrowserLoginOutcomeModel.SignedIn(browserPrincipal)
                justRun { browserSessionAuthenticator.signIn(any(), any(), browserPrincipal) }

                Then("브라우저 세션에 로그인시키고 그 authorize URL 로 302 한다 — 대기 값은 한 번만 쓴다") {
                    val session = MockHttpSession().apply { setAttribute(AuthorizationRequestStore.ATTRIBUTE, pendingAuthorize) }
                    mockMvc.post("/oauth2/login") {
                        this.session = session
                        param("loginId", "user")
                        param("password", "pw")
                    }.andExpect {
                        status { isFound() }
                        redirectedUrl(pendingAuthorize)
                    }

                    verify(exactly = 1) { browserSessionAuthenticator.signIn(any(), any(), browserPrincipal) }
                    session.getAttribute(AuthorizationRequestStore.ATTRIBUTE) shouldBe null
                }
            }

            When("자격 증명이 틀리면") {
                every { authMappingService.loginForBrowser(any(), any()) } throws AuthenticationException("아이디 또는 비밀번호가 올바르지 않습니다")

                Then("같은 화면에 메시지를 보여 주고 로그인시키지 않는다") {
                    mockMvc.post("/oauth2/login") {
                        param("loginId", "user")
                        param("password", "wrong")
                    }.andExpect {
                        status { isOk() }
                        view { name("oauth2/login") }
                        model { attribute("loginError", "아이디 또는 비밀번호가 올바르지 않습니다") }
                    }

                    verify(exactly = 0) { browserSessionAuthenticator.signIn(any(), any(), any()) }
                }
            }
        }

        // ============================================
        // 동의 — approve·deny 어느 분기보다 검증이 먼저. 검증 실패는 HTML 오류 화면, Location 없음
        // ============================================
        Given("POST /oauth2/consent - 검증 선행") {
            // addFilters=false 라 SecurityContextHolderFilter 가 없다 — MockMvc 는 호출 스레드에서 돌므로 홀더에 직접 넣는다
            beforeTest { SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList()) }
            afterTest { SecurityContextHolder.clearContext() }

            When("deny 요청의 redirect_uri 가 등록되지 않은 것이면") {
                every { oAuth2AuthorizationMappingService.consent(any(), 7L, any(), false) } throws
                    OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_REQUEST, "유효하지 않은 redirect_uri입니다")

                Then("400 오류 화면, Location 없음") {
                    mockMvc.post("/oauth2/consent") {
                        param("client_id", "web-app")
                        param("redirect_uri", "https://evil.example/cb")
                        param("approve", "false")
                    }.andExpect {
                        status { isBadRequest() }
                        header { doesNotExist("Location") }
                        view { name("oauth2/error") }
                        model { attribute("error", "invalid_request") }
                    }
                }
            }

            When("approve 요청의 client 가 잘못되면") {
                every { oAuth2AuthorizationMappingService.consent(any(), 7L, any(), true) } throws
                    OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")

                Then("401 오류 화면, Location 없음") {
                    mockMvc.post("/oauth2/consent") {
                        param("client_id", "nope")
                        param("redirect_uri", "https://evil.example/cb")
                        param("approve", "true")
                    }.andExpect {
                        status { isUnauthorized() }
                        header { doesNotExist("Location") }
                        model { attribute("error", "invalid_client") }
                    }
                }
            }

            When("검증을 통과한 deny 요청이면") {
                every { oAuth2AuthorizationMappingService.consent(any(), 7L, any(), false) } returns
                    OAuth2ClientFixture.authorizeDecision(AuthorizeNextStep.ERROR, error = OAuth2AuthorizationBizService.ERROR_ACCESS_DENIED, errorDescription = "사용자가 동의를 거부했습니다")

                Then("검증된 redirect_uri 로 error=access_denied 리다이렉트한다") {
                    mockMvc.post("/oauth2/consent") {
                        param("client_id", "web-app")
                        param("redirect_uri", "https://example.com/callback")
                        param("state", "xyz")
                        param("approve", "false")
                    }.andExpect {
                        status { isFound() }
                        redirectedUrl(
                            "https://example.com/callback?error=access_denied&error_description=%EC%82%AC%EC%9A%A9%EC%9E%90%EA%B0%80+%EB%8F%99%EC%9D%98%EB%A5%BC+%EA%B1%B0%EB%B6%80%ED%96%88%EC%8A%B5%EB%8B%88%EB%8B%A4&state=xyz",
                        )
                    }
                }
            }

            When("검증을 통과한 approve 요청이면") {
                every { oAuth2AuthorizationMappingService.consent(any(), 7L, any(), true) } returns
                    OAuth2ClientFixture.authorizeDecision(AuthorizeNextStep.CODE_ISSUED, code = "code-123")

                Then("검증된 redirect_uri 로 code·state 를 붙여 리다이렉트한다") {
                    mockMvc.post("/oauth2/consent") {
                        param("client_id", "web-app")
                        param("redirect_uri", "https://example.com/callback")
                        param("state", "xyz")
                        param("approve", "true")
                    }.andExpect {
                        status { isFound() }
                        redirectedUrl("https://example.com/callback?code=code-123&state=xyz")
                    }
                }
            }
        }

        Given("GET /oauth2/consent - 동의 화면") {
            When("PKCE 파라미터와 함께 오면") {
                val captured = mutableListOf<AuthorizationRequestDto>()
                every { oAuth2AuthorizationMappingService.getConsentInfo(capture(captured)) } returns
                    AuthorizationConsentModel(
                        clientId = "web-app",
                        clientName = "Web Application",
                        requestedScopes = listOf("openid"),
                        redirectUri = "https://example.com/callback",
                        state = null,
                    )

                Then("동의 화면 뷰에 클라이언트 정보를 싣고, 검증 요청 DTO 에 code_challenge·method·nonce 가 실린다") {
                    mockMvc.get("/oauth2/consent") {
                        param("client_id", "web-app")
                        param("redirect_uri", "https://example.com/callback")
                        param("scope", "openid")
                        param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        param("code_challenge_method", "S256")
                        param("nonce", "n-0S6_WzA2Mj")
                    }.andExpect {
                        status { isOk() }
                        view { name("oauth2/consent") }
                        model { attributeExists("consent", "request") }
                    }
                    captured.single().codeChallenge shouldBe "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                    captured.single().codeChallengeMethod shouldBe "S256"
                    captured.single().nonce shouldBe "n-0S6_WzA2Mj"
                }
            }

            When("client 검증에 실패하면") {
                every { oAuth2AuthorizationMappingService.getConsentInfo(any()) } throws
                    OAuth2Exception(OAuth2AuthorizationBizService.ERROR_INVALID_CLIENT, "유효하지 않은 클라이언트입니다")

                Then("401 오류 화면") {
                    mockMvc.get("/oauth2/consent") {
                        param("client_id", "nope")
                        param("redirect_uri", "https://evil.example/cb")
                    }.andExpect {
                        status { isUnauthorized() }
                        view { name("oauth2/error") }
                    }
                }
            }
        }
    }
}