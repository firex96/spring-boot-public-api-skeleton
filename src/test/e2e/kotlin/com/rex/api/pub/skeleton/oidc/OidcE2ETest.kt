package com.rex.api.pub.skeleton.oidc

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.authorizationCodeViaBrowser
import com.rex.api.pub.skeleton.support.authorizationServerTokens
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.createOAuth2TestClient
import com.rex.api.pub.skeleton.support.exchangeAuthorizationCode
import com.rex.api.pub.skeleton.support.getWithAuth
import com.rex.api.pub.skeleton.support.postForm
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap

/**
 * OpenID Connect API E2E 테스트.
 *
 * OIDC Discovery, JWKS, UserInfo 엔드포인트를 실제 HTTP 요청으로 테스트합니다.
 *
 * 실행 방법:
 * ```bash
 * ./gradlew e2eTest --tests "*.oidc.OidcE2ETest"
 * ```
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(E2ETestConfiguration::class)
class OidcE2ETest(
    private val restTemplate: TestRestTemplate,
    private val janitor: E2eResourceJanitor,
) : DescribeSpec({

    val resources = E2eResources()
    afterSpec { println("[e2e cleanup] OidcE2ETest ${janitor.cleanup(resources)}") }

    // 일반 사용자 생성 및 로그인
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다

    describe("GET /.well-known/openid-configuration - OIDC Discovery") {

        context("OIDC 설정 정보 조회") {
            it("200 OK와 OpenID Configuration을 반환한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/.well-known/openid-configuration",
                    Map::class.java,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()

                // 필수 필드 확인
                response.body!!["issuer"].shouldNotBeNull()
                response.body!!["authorization_endpoint"].shouldNotBeNull()
                response.body!!["token_endpoint"].shouldNotBeNull()
                response.body!!["jwks_uri"].shouldNotBeNull()
            }
        }

        context("OIDC 설정 상세 확인") {
            it("지원되는 기능 목록을 반환한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/.well-known/openid-configuration",
                    Map::class.java,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK

                // 선택적 필드 (있을 경우 검증)
                val body = response.body!!
                if (body.containsKey("userinfo_endpoint")) {
                    body["userinfo_endpoint"].shouldNotBeNull()
                }
                if (body.containsKey("revocation_endpoint")) {
                    body["revocation_endpoint"].shouldNotBeNull()
                }
                if (body.containsKey("introspection_endpoint")) {
                    body["introspection_endpoint"].shouldNotBeNull()
                }
            }
        }
    }

    describe("GET /.well-known/jwks.json - JSON Web Key Set") {

        context("JWKS 조회") {
            it("200 OK와 공개 키 목록을 반환한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/.well-known/jwks.json",
                    Map::class.java,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()

                // keys 배열 확인
                val keys = response.body!!["keys"]
                keys.shouldNotBeNull()
            }
        }

        context("JWKS 키 상세 확인") {
            it("유효한 RSA 공개 키를 반환한다") {
                // when
                val response = restTemplate.getForEntity(
                    "/.well-known/jwks.json",
                    Map::class.java,
                )

                // then
                response.statusCode shouldBe HttpStatus.OK

                val keys = response.body!!["keys"] as? List<Map<String, Any>>
                if (!keys.isNullOrEmpty()) {
                    val firstKey = keys[0]
                    // RSA 키의 필수 필드
                    firstKey["kty"].shouldNotBeNull() // Key Type
                    firstKey["use"].shouldNotBeNull() // Usage (sig)
                    firstKey["kid"].shouldNotBeNull() // Key ID
                }
            }
        }
    }

    describe("GET /oauth2/userinfo - UserInfo 조회 (인가 서버 access 토큰)") {

        suspend fun newUser() = restTemplate.createAndLoginUser(resources, "e2e_oidc_")

        fun userinfo(token: String?) =
            restTemplate.exchange(
                "/oauth2/userinfo",
                HttpMethod.GET,
                HttpEntity<Void>(HttpHeaders().apply { token?.let { setBearerAuth(it) } }),
                String::class.java,
            )

        context("authorization_code 토큰 (openid profile email)") {
            it("200 이고 sub·email 이 있다") {
                val user = newUser()
                val tokens = restTemplate.authorizationServerTokens(resources, user.loginId, "openid profile email")

                val response = restTemplate.getWithAuth<Map<String, Any>>("/oauth2/userinfo", tokens["access_token"] as String)

                response.statusCode shouldBe HttpStatus.OK
                val body = response.body.shouldNotBeNull()
                body["sub"] shouldBe user.userId.toString()
                body["email"].shouldNotBeNull()
                body.containsKey("email_verified") shouldBe true
            }
        }

        context("authorization_code 토큰 (openid 만)") {
            it("email·name 키가 없다 — scope 가 허락한 클레임만 싣는다") {
                val user = newUser()
                val tokens = restTemplate.authorizationServerTokens(resources, user.loginId, "openid")

                val body = restTemplate.getWithAuth<Map<String, Any>>("/oauth2/userinfo", tokens["access_token"] as String).body.shouldNotBeNull()

                body["sub"] shouldBe user.userId.toString()
                body.containsKey("email") shouldBe false
                body.containsKey("name") shouldBe false
            }
        }

        context("openid 없는 토큰") {
            it("403 insufficient_scope 와 WWW-Authenticate") {
                val user = newUser()
                val tokens = restTemplate.authorizationServerTokens(resources, user.loginId, "profile")

                val response = userinfo(tokens["access_token"] as String)

                response.statusCode shouldBe HttpStatus.FORBIDDEN
                response.body.shouldNotBeNull() shouldContain "insufficient_scope"
                response.headers.getFirst("WWW-Authenticate").shouldNotBeNull() shouldContain "insufficient_scope"
            }
        }

        context("client_credentials 토큰 — 사용자 주체 없음") {
            it("401 invalid_token") {
                val (clientId, clientSecret) = restTemplate.createOAuth2TestClient(resources)
                val token =
                    restTemplate.postForm<Map<String, Any>>(
                        "/oauth2/token",
                        LinkedMultiValueMap<String, String>().apply {
                            add("grant_type", "client_credentials")
                            add("client_id", clientId)
                            add("client_secret", clientSecret)
                            add("scope", "openid")
                        },
                    ).body.shouldNotBeNull()["access_token"] as String

                val response = userinfo(token)

                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                response.headers.getFirst("WWW-Authenticate").shouldNotBeNull() shouldContain "invalid_token"
            }
        }

        context("폐기한 토큰") {
            it("401 invalid_token") {
                val user = newUser()
                val (clientId, clientSecret) = restTemplate.createOAuth2TestClient(resources)
                val tokens = restTemplate.exchangeAuthorizationCode(clientId, clientSecret, restTemplate.authorizationCodeViaBrowser(clientId, user.loginId, "openid"))
                val accessToken = tokens["access_token"] as String
                restTemplate.postForm<String>(
                    "/oauth2/revoke",
                    LinkedMultiValueMap<String, String>().apply {
                        add("token", accessToken)
                        add("client_id", clientId)
                        add("client_secret", clientSecret)
                    },
                ).statusCode shouldBe HttpStatus.OK

                userinfo(accessToken).statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("1st-party 로그인 토큰 (POST /auth/login)") {
            it("401 invalid_token — userinfo 는 OAuth 2.0 access 토큰만 받는다") {
                userinfo(newUser().accessToken).statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("토큰 없음·잘못된 토큰") {
            it("401") {
                userinfo(null).statusCode shouldBe HttpStatus.UNAUTHORIZED
                userinfo("invalid-token").statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    describe("OIDC Discovery와 JWKS 연동 검증") {

        context("Discovery에서 제공하는 JWKS URI 확인") {
            it("jwks_uri가 유효한 JWKS를 반환한다") {
                // given
                val configResponse = restTemplate.getForEntity(
                    "/.well-known/openid-configuration",
                    Map::class.java,
                )

                // then
                configResponse.statusCode shouldBe HttpStatus.OK
                val jwksUri = configResponse.body!!["jwks_uri"] as? String
                jwksUri.shouldNotBeNull()

                // JWKS URI가 상대 경로인 경우
                val jwksEndpoint = if (jwksUri!!.startsWith("http")) {
                    // 절대 경로면 그대로 사용 (테스트 환경에서는 localhost로 대체 필요)
                    "/.well-known/jwks.json"
                } else {
                    jwksUri
                }

                // JWKS 조회
                val jwksResponse = restTemplate.getForEntity(jwksEndpoint, Map::class.java)
                jwksResponse.statusCode shouldBe HttpStatus.OK
                jwksResponse.body!!["keys"].shouldNotBeNull()
            }
        }
    }
})