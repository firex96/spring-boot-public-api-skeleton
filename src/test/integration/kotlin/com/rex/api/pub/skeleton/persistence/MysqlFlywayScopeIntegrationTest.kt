package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.persistence.FlywayProperties
import com.rex.api.pub.skeleton.config.persistence.MysqlFlywayConfiguration
import com.rex.api.pub.skeleton.config.persistence.MysqlFlywayProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class MysqlFlywayScopeIntegrationTest : StringSpec({
    "초기화 비활성 시 admin 자격증명과 데이터소스를 요구하지 않는다" {
        AnnotationConfigApplicationContext().use { context ->
            TestPropertyValues.of("spring.flyway.enabled=false").applyTo(context)
            context.register(MysqlFlywayConfiguration::class.java)
            context.refresh()
            context.getBeansOfType(Flyway::class.java).isEmpty() shouldBe true
        }
    }
    "skeleton_auth 가 아닌 스키마·다른 DB 엔진 URL은 연결 전에 거부한다" {
        val configuration = MysqlFlywayConfiguration(FlywayProperties(), MysqlFlywayProperties("unused", "unused"))
        listOf(
            "jdbc:mysql://localhost/skeleton_main",
            "jdbc:mysql://localhost/skeleton_auth_extra",
            "jdbc:postgresql://localhost/skeleton_auth",
        ).forEach { url ->
            shouldThrow<IllegalArgumentException> {
                configuration.flywayAuth(DataSourceProperties().apply { this.url = url })
            }
        }
    }
})