package com.rex.api.pub.skeleton.config.persistence

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource

@Configuration
class AuthJdbcConfiguration {
    @Bean
    fun authJdbcTemplate(
        @Qualifier("authDataSource") authDataSource: DataSource,
    ): JdbcTemplate = JdbcTemplate(authDataSource)

    @Bean
    fun authNamedParameterJdbcOperations(
        @Qualifier("authDataSource") authDataSource: DataSource,
    ): NamedParameterJdbcOperations = NamedParameterJdbcTemplate(authDataSource)
}