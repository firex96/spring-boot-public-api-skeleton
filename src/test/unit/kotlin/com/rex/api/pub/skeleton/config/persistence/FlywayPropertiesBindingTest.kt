package com.rex.api.pub.skeleton.config.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.springframework.boot.context.properties.bind.BindHandler
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.FileSystemResource

/**
 * `flyway.*` 가 [FlywayProperties] 에 실제로 바인딩되는지 프로파일 yml 을 그대로 읽어 고정한다.
 *
 * `flyway.auth:` 바로 아래에 `locations:` 키 없이 목록만 두면 relaxed binding 이 `auth.locations` 를 채우지 못해
 * 빈 리스트로 조용히 지나가고 Flyway 는 0 migrations 를 적용한다. `local` 프로파일은 override 가 있어 기동으로는
 * 기본 yml 의 결함이 드러나지 않으므로 유닛으로 잡는다. [NoUnboundElementsBindHandler] 가 `flyway` 아래에 바인딩되지 않는
 * 키(들여쓰기 실수로 굴러들어온 다른 블록)가 없음을 함께 강제한다.
 *
 * 유닛 테스트 리소스에도 `application.yml` 이 있어 클래스패스가 아니라 `src/main/resources` 파일 경로로 읽는다.
 */
class FlywayPropertiesBindingTest : BehaviorSpec({

    fun bind(fileName: String): FlywayProperties {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader()
            .load(fileName, FileSystemResource("src/main/resources/$fileName"))
            .forEach { environment.propertySources.addLast(it) }
        return Binder(ConfigurationPropertySources.get(environment))
            .bind("flyway", Bindable.of(FlywayProperties::class.java), NoUnboundElementsBindHandler(BindHandler.DEFAULT))
            .get()
    }

    Given("기본 application.yml") {
        Then("F-1 auth.locations 는 ddl 과 dml/mandatory 두 위치를 담는다") {
            bind("application.yml").auth.locations shouldContainExactly
                listOf("classpath:db/migration/auth/ddl", "classpath:db/migration/auth/dml/mandatory")
        }
    }

    Given("application-local.yml") {
        Then("F-2 auth.locations 는 ddl·dml/mandatory·dml/test 세 위치이고 flyway 아래에 바인딩되지 않는 키가 없다 (F-3)") {
            bind("application-local.yml").auth.locations shouldHaveSize 3
        }
    }
})