package com.rex.api.pub.skeleton.persistence

import com.rex.api.pub.skeleton.config.persistence.MysqlFlywayConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.exception.FlywayValidateException
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.MySQLContainer
import java.util.function.Supplier

/** 실제 애플리케이션 Flyway 빈으로 빈 DB 초기화와 재실행/체크섬 실패를 검증한다. */
class MysqlSchemaInitializationIntegrationTest : StringSpec({
    "MySQL 초기화는 레포 이력과 소유 테이블만 생성하고 재실행 시 중복하지 않는다" {
        val mysql = MySQLContainer("mysql:8.0").withDatabaseName("skeleton_auth")
        mysql.start()
        val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        fun context(enabled: Boolean): AnnotationConfigApplicationContext {
            val context = AnnotationConfigApplicationContext()
            TestPropertyValues.of(
                "spring.flyway.enabled=$enabled",
                "app.flyway.mysql.username=${mysql.username}",
                "app.flyway.mysql.password=${mysql.password}",
                "flyway.auth.locations[0]=classpath:db/migration/auth/ddl",
                "flyway.auth.locations[1]=classpath:db/migration/auth/dml/mandatory",
            ).applyTo(context)
            context.registerBean(
                "authWriteDataSourceProperties",
                DataSourceProperties::class.java,
                Supplier {
                    DataSourceProperties().apply { url = mysql.jdbcUrl }
                },
            )
            context.register(MysqlFlywayConfiguration::class.java)
            context.refresh()
            return context
        }
        try {
            val jdbc = JdbcTemplate(dataSource)
            context(false).use { disabled ->
                disabled.getBeansOfType(Flyway::class.java).isEmpty() shouldBe true
                jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Long::class.java) shouldBe 0L
            }
            context(true).use { context ->
                jdbc.queryForObject("SELECT DATABASE()", String::class.java) shouldBe "skeleton_auth"
                val flyway = context.getBean("flywayAuth", Flyway::class.java)
                flyway.configuration.table shouldBe "flyway_schema_history_public_api"
                flyway.validate()
                flyway.migrate().migrationsExecuted shouldBe 0
                jdbc.queryForObject("SELECT COUNT(*) FROM users", Long::class.java) shouldBe 0L
                jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long::class.java) shouldBe 3L
                (jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Long::class.java)!! > 0L) shouldBe true
                jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'skeleton\\_%'", Long::class.java) shouldBe 0L
                // DDL 이 명시한 콜레이션 — 새 DB 의 테이블·문자열 컬럼은 전부 utf8mb4_0900_ai_ci (이력 테이블은 Flyway 가 서버 기본으로 만들어 제외)
                jdbc.queryForList(
                    "SELECT DISTINCT table_collation FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name NOT LIKE 'flyway\\_schema\\_history%'",
                    String::class.java,
                ) shouldBe listOf("utf8mb4_0900_ai_ci")
                jdbc.queryForList(
                    "SELECT DISTINCT collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND collation_name IS NOT NULL AND table_name NOT LIKE 'flyway\\_schema\\_history%'",
                    String::class.java,
                ) shouldBe listOf("utf8mb4_0900_ai_ci")
                jdbc.update("INSERT INTO users(login_id, name) VALUES ('schema-test', 'Schema Test')")
                jdbc.queryForObject("SELECT name FROM users WHERE login_id='schema-test'", String::class.java) shouldBe "Schema Test"
                jdbc.update("DELETE FROM users WHERE login_id='schema-test'")
            }
            context(true).use { it.getBean("flywayAuth", Flyway::class.java).validate() }
            jdbc.update("UPDATE flyway_schema_history_public_api SET checksum=checksum+1 WHERE version='1'")
            val validation = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration/auth/ddl", "classpath:db/migration/auth/dml/mandatory")
                .table("flyway_schema_history_public_api").load()
            shouldThrow<FlywayValidateException> { validation.validate() }
        } finally {
            mysql.stop()
        }
    }
})