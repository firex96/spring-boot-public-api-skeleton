package com.rex.api.pub.skeleton.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.SpringApplication
import org.springframework.boot.bootstrap.DefaultBootstrapContext
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

/**
 * 미해석 플레이스홀더 fail-fast 가드의 계약.
 *
 * **G-1 이 핵심이다** — Boot 바인더는 해석 불가 `${…}` 를 리터럴로 남겨 조용히 뜨게 하므로, 가드가 없으면
 * 논리 키 오타가 첫 사용 시점까지 숨는다. G-2·G-3 은 정상 설정(기본값 있는 플레이스홀더, 해석 가능한 참조)이
 * 오탐되지 않음을, G-4 는 `application*.yml` 이 아닌 소스(OS 환경변수 등)를 보지 않음을 고정한다.
 */
class UnresolvedPlaceholderGuardTest : BehaviorSpec({

    fun environmentWith(
        sourceName: String,
        properties: Map<String, Any>,
    ): StandardEnvironment =
        StandardEnvironment().also {
            it.propertySources.addFirst(MapPropertySource(sourceName, properties))
        }

    Given("application yml 소스에 해석되지 않는 플레이스홀더가 있을 때") {
        val environment =
            environmentWith(
                "Config resource 'class path resource [application-local.yml]'",
                mapOf("jwt.secret" to "\${jwt-secret-typo}"),
            )

        Then("G-1 프로퍼티 이름과 원문을 들고 IllegalStateException 으로 기동을 막는다") {
            UnresolvedPlaceholderGuard.findUnresolved(environment) shouldContainExactly
                listOf("jwt.secret -> \${jwt-secret-typo}")

            val exception =
                shouldThrow<IllegalStateException> {
                    UnresolvedPlaceholderGuard().onApplicationEvent(
                        ApplicationEnvironmentPreparedEvent(DefaultBootstrapContext(), SpringApplication(), emptyArray(), environment),
                    )
                }
            exception.message shouldContain "해석되지 않은 설정 플레이스홀더"
            exception.message shouldContain "jwt.secret -> \${jwt-secret-typo}"
        }
    }

    Given("기본값이 있는 플레이스홀더만 있을 때") {
        val environment =
            environmentWith(
                "Config resource 'class path resource [application-local.yml]'",
                mapOf("server.ssl.certificate" to "file:\${LOCAL_CERT_DIR:\${user.home}/.certs}/local.crt"),
            )

        Then("G-2 통과한다 — 기본값이 해석된다") {
            UnresolvedPlaceholderGuard.findUnresolved(environment).shouldBeEmpty()
        }
    }

    Given("다른 소스가 값을 주는 플레이스홀더가 있을 때") {
        val environment =
            environmentWith(
                "Config resource 'class path resource [application-local.yml]'",
                mapOf("jwt.secret" to "\${jwt-secret}"),
            ).also {
                it.propertySources.addLast(
                    MapPropertySource("cloud-secret-property-source", mapOf("jwt-secret" to "resolved")),
                )
            }

        Then("G-3 통과한다 — 클라우드 비밀 소스처럼 뒤에 붙은 소스로 해석된다") {
            UnresolvedPlaceholderGuard.findUnresolved(environment).shouldBeEmpty()
        }
    }

    Given("application yml 이 아닌 소스에만 해석되지 않는 플레이스홀더가 있을 때") {
        val environment = environmentWith("systemEnvironment-like", mapOf("SOMETHING" to "\${nope}"))

        Then("G-4 보지 않는다 — 대상은 Config resource 소스뿐이다") {
            UnresolvedPlaceholderGuard.findUnresolved(environment).shouldBeEmpty()
        }
    }

    Given("베이스 yml 의 논리 키 참조를 상위 소스(테스트 프로파일 yml)가 같은 이름으로 덮을 때") {
        val environment =
            StandardEnvironment().also {
                // 나중에 addFirst 한 것이 우선순위가 높다 — 테스트 프로파일 yml 이 베이스를 덮는 모양
                it.propertySources.addFirst(
                    MapPropertySource(
                        "Config resource 'class path resource [application.yml]'",
                        mapOf("app.flyway.mysql.username" to "\${db-admin-username}"),
                    ),
                )
                it.propertySources.addFirst(
                    MapPropertySource(
                        "Config resource 'class path resource [application-test.yml]'",
                        mapOf("app.flyway.mysql.username" to "test-admin"),
                    ),
                )
            }

        Then("G-5 이기는 소스의 값만 본다 — 덮인 하위 yml 의 플레이스홀더는 쓰이지 않으므로 통과") {
            UnresolvedPlaceholderGuard.findUnresolved(environment).shouldBeEmpty()
        }
    }

    Given("Boot 가 붙이는 집계 소스(configurationProperties)가 최상위에 있을 때") {
        val environment =
            environmentWith(
                "Config resource 'class path resource [application-local.yml]'",
                mapOf("jwt.secret" to "\${jwt-secret-typo}"),
            ).also { ConfigurationPropertySources.attach(it) }

        Then("G-6 집계 소스를 이기는 소스로 오판하지 않고 검출한다 — 실제 기동 환경과 같은 모양") {
            UnresolvedPlaceholderGuard.findUnresolved(environment) shouldContainExactly
                listOf("jwt.secret -> \${jwt-secret-typo}")
        }
    }
})