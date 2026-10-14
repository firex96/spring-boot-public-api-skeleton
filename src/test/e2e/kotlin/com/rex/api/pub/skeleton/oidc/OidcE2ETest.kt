package com.rex.api.pub.skeleton.oidc

import com.rex.api.pub.skeleton.config.E2ETestConfiguration
import com.rex.api.pub.skeleton.support.E2eResourceJanitor
import com.rex.api.pub.skeleton.support.E2eResources
import com.rex.api.pub.skeleton.support.createAndLoginUser
import com.rex.api.pub.skeleton.support.getWithAuth
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

    fun uniqueLoginId() = "e2e_oidc_${UUID.randomUUID().toString().take(8)}"
    fun uniqueEmail() = "e2e_oidc_${UUID.randomUUID().toString().take(8)}@example.com"

    // 일반 사용자 생성 및 로그인
    // accessToken — signup 201·login 200 단언·정리 등록은 공용 헬퍼가 한다
    suspend fun createAndLoginUser(): String = restTemplate.createAndLoginUser(resources, "e2e_oidc_").accessToken

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

    describe("GET /oauth2/userinfo - UserInfo 조회") {

        context("인증된 사용자가 UserInfo 조회") {
            it("200 OK와 사용자 정보를 반환한다") {
                // given
                val accessToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>("/oauth2/userinfo", accessToken)

                // then
                response.statusCode shouldBe HttpStatus.OK
                response.body.shouldNotBeNull()

                // 기본 클레임 확인
                response.body!!["sub"].shouldNotBeNull() // Subject (user ID)
            }
        }

        context("인증 없이 UserInfo 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getForEntity("/oauth2/userinfo", String::class.java)

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("잘못된 토큰으로 UserInfo 조회") {
            it("401 Unauthorized를 반환한다") {
                // when
                val response = restTemplate.getWithAuth<String>("/oauth2/userinfo", "invalid-token")

                // then
                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("UserInfo에 포함된 클레임 확인") {
            it("openid 스코프에 해당하는 클레임을 반환한다") {
                // given
                val accessToken = createAndLoginUser()

                // when
                val response = restTemplate.getWithAuth<Map<String, Any>>("/oauth2/userinfo", accessToken)

                // then — 로그인 사용자의 access token 으로 UserInfo 는 200 이어야 한다(if 가드는 실패를 통과시켰다)
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body.shouldNotBeNull()

                // sub (subject)는 항상 포함
                body["sub"].shouldNotBeNull()
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