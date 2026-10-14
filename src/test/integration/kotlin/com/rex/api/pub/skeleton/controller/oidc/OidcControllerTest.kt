package com.rex.api.pub.skeleton.controller.oidc

import com.ninjasquad.springmockk.MockkBean
import com.rex.api.pub.skeleton.model.oidc.OpenIdConfigurationModel
import com.rex.api.pub.skeleton.service.mapping.OidcMappingService
import com.rex.api.pub.skeleton.support.ControllerTestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * OidcController 통합 테스트.
 *
 * OIDC Discovery, JWKS, UserInfo 엔드포인트를 테스트합니다.
 *
 * 테스트 실행:
 * ```bash
 * ./gradlew integrationTest --tests "*.controller.oidc.OidcControllerTest"
 * ```
 */
@WebMvcTest(controllers = [OidcController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerTestConfiguration::class)
class OidcControllerTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec() {
    @MockkBean
    lateinit var mappingService: OidcMappingService

    init {
        // ============================================
        // OpenID Connect Discovery
        // ============================================
        Given("GET /.well-known/openid-configuration - OIDC Discovery") {
            When("OIDC 설정 정보 조회") {
                val config = OpenIdConfigurationModel(
                    issuer = "https://auth.example.com",
                    authorizationEndpoint = "https://auth.example.com/oauth2/authorize",
                    tokenEndpoint = "https://auth.example.com/oauth2/token",
                    userinfoEndpoint = "https://auth.example.com/oauth2/userinfo",
                    jwksUri = "https://auth.example.com/.well-known/jwks.json",
                    revocationEndpoint = "https://auth.example.com/oauth2/revoke",
                    introspectionEndpoint = "https://auth.example.com/oauth2/introspect",
                )

                every { mappingService.getOpenIdConfiguration() } returns config

                Then("200 OK와 OIDC 설정 정보 반환") {
                    mockMvc.get("/.well-known/openid-configuration")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.issuer") { value("https://auth.example.com") }
                            jsonPath("$.authorization_endpoint") { value("https://auth.example.com/oauth2/authorize") }
                            jsonPath("$.token_endpoint") { value("https://auth.example.com/oauth2/token") }
                            jsonPath("$.userinfo_endpoint") { value("https://auth.example.com/oauth2/userinfo") }
                            jsonPath("$.jwks_uri") { value("https://auth.example.com/.well-known/jwks.json") }
                        }
                }
            }
        }

        // ============================================
        // JSON Web Key Set
        // ============================================
        Given("GET /.well-known/jwks.json - JWKS 조회") {
            When("공개 키 목록 조회") {
                val jwks = mapOf(
                    "keys" to listOf(
                        mapOf(
                            "kty" to "RSA",
                            "use" to "sig",
                            "kid" to "test-key-id",
                            "alg" to "RS256",
                            "n" to "test-modulus",
                            "e" to "AQAB",
                        ),
                    ),
                )

                every { mappingService.getJwkSet() } returns jwks

                Then("200 OK와 JWKS 반환") {
                    mockMvc.get("/.well-known/jwks.json")
                        .andExpect {
                            status { isOk() }
                            jsonPath("$.keys") { isArray() }
                            jsonPath("$.keys[0].kty") { value("RSA") }
                            jsonPath("$.keys[0].use") { value("sig") }
                            jsonPath("$.keys[0].alg") { value("RS256") }
                        }
                }
            }
        }

        // ============================================
        // UserInfo
        // ============================================
        // 참고: UserInfo 엔드포인트는 @AuthenticationPrincipal을 사용하며,
        // 보안 필터가 비활성화된 @WebMvcTest에서는 테스트할 수 없습니다.
        // 이 엔드포인트의 테스트는 E2E 테스트에서 수행합니다.
    }
}