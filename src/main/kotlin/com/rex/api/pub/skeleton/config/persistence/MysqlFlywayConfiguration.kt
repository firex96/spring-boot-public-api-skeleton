package com.rex.api.pub.skeleton.config.persistence

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.net.URI

/** 사전 생성된 MySQL 스키마에 이 레포 소유 테이블만 초기화한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["spring.flyway.enabled"], havingValue = "true")
@Import(DatabaseInitializationDependencyConfigurer::class)
@EnableConfigurationProperties(FlywayProperties::class, MysqlFlywayProperties::class)
class MysqlFlywayConfiguration(
    private val flywayProperties: FlywayProperties,
    private val mysqlFlywayProperties: MysqlFlywayProperties,
) {
    @Bean(initMethod = "migrate")
    fun flywayAuth(
        @Qualifier("authWriteDataSourceProperties") authWriteDataSourceProperties: DataSourceProperties,
    ): Flyway = Flyway.configure()
        .dataSource(skeletonUrl(authWriteDataSourceProperties, "skeleton_auth"), mysqlFlywayProperties.username, mysqlFlywayProperties.password)
        .locations(*flywayProperties.auth.locations.toTypedArray())
        .table("flyway_schema_history_public_api")
        .createSchemas(false)
        .load()

    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy = FlywayMigrationStrategy { }

    private fun skeletonUrl(properties: DataSourceProperties, expectedSchema: String): String {
        val url = properties.determineUrl()
        require(url.startsWith("jdbc:mysql://")) { "Flyway requires a skeleton MySQL URL" }
        val uri = URI.create(url.removePrefix("jdbc:"))
        require(uri.rawPath == "/$expectedSchema" && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null) {
            "Flyway catalog must be $expectedSchema"
        }
        return url
    }
}