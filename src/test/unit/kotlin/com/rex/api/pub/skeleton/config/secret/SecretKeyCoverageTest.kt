package com.rex.api.pub.skeleton.config.secret

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/**
 * 논리 키 계약이 어긋나지 않는지 확인한다 — 기본 `application.yml` 이 참조하는 논리 키(kebab-case `${…}`)가
 *
 * - `.env.example` 에 환경변수 이름(UPPER_SNAKE)으로 모두 적혀 있고,
 * - 비밀 키(`-username`·`-password`·`-secret`·`-key` 로 끝나는 키)는 `application-gcp.yml` 에 `${sm@같은이름}` 으로 대응돼 있다.
 *
 * 주석 줄(`# rsa-private-key: ${…}` 처럼 켜지 않은 기능의 안내)은 보지 않는다 — 켤 때 함께 반영한다.
 * GCP Secret Manager 는 이름으로 일괄 노출하지 않아 키마다 대응을 적어야 하고, 공식 에뮬레이터가 없어 기동 테스트 대신 이 검사로 누락을 잡는다.
 */
class SecretKeyCoverageTest : StringSpec({
    val resources = File("src/main/resources")
    val logicalKeys =
        LOGICAL_KEY
            .findAll(
                File(resources, "application.yml").readLines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n"),
            ).map { it.groupValues[1] }.toSortedSet()
    val secretKeys = logicalKeys.filter { key -> SECRET_SUFFIXES.any { key.endsWith(it) } }

    "기본 yml 의 논리 키는 모두 .env.example 에 환경변수 이름으로 적혀 있다" {
        val envExample = File(".env.example").readText()
        logicalKeys
            .map { it.uppercase().replace('-', '_') }
            .filterNot { Regex("""(?m)^#?\s*$it=""").containsMatchIn(envExample) }
            .shouldBeEmpty()
    }

    "비밀 논리 키는 모두 application-gcp.yml 에 같은 이름의 Secret Manager 비밀로 대응돼 있다" {
        val gcp = File(resources, "application-gcp.yml").readText()
        secretKeys
            .filterNot { Regex("""(?m)^$it: \${'$'}\{sm@$it\}""").containsMatchIn(gcp) }
            .shouldBeEmpty()
    }
}) {
    companion object {
        private val LOGICAL_KEY = Regex("""\$\{([a-z][a-z0-9]*(?:-[a-z0-9]+)+)""")
        private val SECRET_SUFFIXES = listOf("-username", "-password", "-secret", "-key")
    }
}