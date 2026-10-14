package com.rex.api.pub.skeleton.config.persistence

import com.rex.api.pub.skeleton.converter.ProductStatusReadingConverter
import com.rex.api.pub.skeleton.converter.StringEnumWritingConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource

@Configuration
@EnableJdbcRepositories(
    basePackages = [
        "com.rex.api.pub.skeleton.persistence.jdbc.repository.main",
    ],
    transactionManagerRef = "mainTransactionManager",
)
class MainJdbcConfiguration {
    @Primary
    @Bean
    fun mainJdbcTemplate(
        @Qualifier("mainDataSource") dataSource: DataSource,
    ): JdbcTemplate = JdbcTemplate(dataSource)

    @Primary
    @Bean
    fun mainNamedParameterJdbcOperations(
        @Qualifier("mainDataSource") dataSource: DataSource,
    ): NamedParameterJdbcOperations = NamedParameterJdbcTemplate(dataSource)

    @Primary
    @Bean
    fun jdbcCustomConversions(): JdbcCustomConversions = JdbcCustomConversions(
        listOf(
            ProductStatusReadingConverter(),
            StringEnumWritingConverter(),
        ),
    )
}