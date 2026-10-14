package com.rex.api.pub.skeleton.config

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource

/**
 * 설정 파일 값에 해석되지 않은 `${…}` 가 남아 있으면 기동을 막는다.
 *
 * Spring Boot 의 `@ConfigurationProperties` 바인딩은 해석 불가 플레이스홀더를 **리터럴 문자열로 남긴다**
 * (`PropertySourcesPlaceholdersResolver` 의 `ignoreUnresolvablePlaceholders`). 그래서 논리 키 오타·비밀 미등록·
 * 클라우드 자격증명 만료가 「조용한 미설정」으로 지나가고 — 예컨대 `"${jwt-secret-typo}"` 라는 문자열이 서명키가 되어
 * 뜬 뒤 첫 사용에서야 실패한다. 설정값·시크릿 규약(`.claude/rules/conventions.md`) 「참조는 폴백 없이 엄격하다」를
 * 이 리스너가 보증한다.
 *
 * - 대상은 `Config resource` 로 시작하는 property source, 즉 `application*.yml` 이다. OS 환경변수·클라우드 비밀 소스는 보지 않는다.
 * - `${name:default}` 처럼 기본값이 있는 것은 해석되므로 통과한다.
 * - `ApplicationEnvironmentPreparedEvent` 를 **가장 늦게** 받는다 — Azure Key Vault 처럼 `EnvironmentPostProcessor` 로
 *   비밀 소스를 붙이는 연동이 끝난 뒤여야 한다. `spring.factories` 로 등록되어 컴포넌트 스캔 전에 동작한다.
 * - 메시지에는 프로퍼티 이름과 플레이스홀더 원문만 쓴다. 시크릿 값은 어디에도 쓰지 않는다.
 */
class UnresolvedPlaceholderGuard :
    ApplicationListener<ApplicationEnvironmentPreparedEvent>,
    Ordered {
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        val unresolved = findUnresolved(event.environment)
        check(unresolved.isEmpty()) {
            "해석되지 않은 설정 플레이스홀더 — 환경변수·비밀 저장소에 논리 키가 있는지, 프로필·자격증명이 맞는지 확인한다:\n" +
                unresolved.joinToString("\n")
        }
    }

    companion object {
        private const val CONFIG_RESOURCE_PREFIX = "Config resource"

        /** `ConfigurationPropertySources.attach` 가 붙이는 집계 소스 이름 — Boot 의 상수가 private 이라 값을 적는다. */
        private const val ATTACHED_PROPERTY_SOURCE_NAME = "configurationProperties"
        private const val PLACEHOLDER_START = "\${"

        /**
         * 해석되지 않은 플레이스홀더를 `프로퍼티 이름 -> 원문` 형태로 돌려준다. 비어 있으면 통과다.
         *
         * 프로퍼티마다 **실제로 이기는 소스**(우선순위가 가장 높은 소스)의 원문만 본다 — 테스트 프로파일 yml·
         * `@TestPropertySource` 처럼 상위 소스가 같은 이름을 덮으면 하위 yml 의 `${…}` 는 쓰이지 않으므로 검사하지 않는다.
         * 이기는 소스가 `application*.yml` 이 아니면(OS 환경변수 등) 그 값은 이 가드의 대상이 아니다.
         */
        fun findUnresolved(environment: ConfigurableEnvironment): List<String> {
            // Boot 가 최상위에 붙이는 집계 소스(ConfigurationPropertySources.attach)는 모든 이름에 containsProperty=true 를
            // 돌려주므로 「이기는 소스」 판정에서 빼야 한다 — 안 빼면 아무것도 검사하지 않고 통과한다(2026-09-15 실측).
            val sources =
                environment.propertySources.filter { it.name != ATTACHED_PROPERTY_SOURCE_NAME }
            val configNames =
                sources
                    .filterIsInstance<EnumerablePropertySource<*>>()
                    .filter { it.name.startsWith(CONFIG_RESOURCE_PREFIX) }
                    .flatMap { it.propertyNames.asList() }
                    .distinct()
            return configNames.mapNotNull { name ->
                val winner = sources.firstOrNull { it.containsProperty(name) } ?: return@mapNotNull null
                if (!winner.name.startsWith(CONFIG_RESOURCE_PREFIX)) return@mapNotNull null
                val raw = winner.getProperty(name)?.toString() ?: return@mapNotNull null
                if (!raw.contains(PLACEHOLDER_START)) return@mapNotNull null
                runCatching { environment.resolveRequiredPlaceholders(raw) }
                    .exceptionOrNull()
                    ?.let { "$name -> $raw" }
            }
        }
    }
}