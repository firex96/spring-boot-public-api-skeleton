package com.rex.api.pub.skeleton.config.persistence

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

@Configuration
class AuthDataSourceConfiguration {
    @Bean("authWriteDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.auth.write")
    fun authWriteDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean("authReadDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.auth.read")
    fun authReadDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean("authWriteDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.auth.write.hikari")
    fun authWriteDataSource(
        @Qualifier("authWriteDataSourceProperties") properties: DataSourceProperties,
    ): DataSource = properties.initializeDataSourceBuilder().build()

    @Bean("authReadDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.auth.read.hikari")
    fun authReadDataSource(
        @Qualifier("authReadDataSourceProperties") properties: DataSourceProperties,
    ): DataSource = properties.initializeDataSourceBuilder().build()

    @Bean("authDataSource")
    fun authDataSource(
        @Qualifier("authWriteDataSource") writeDataSource: DataSource,
        @Qualifier("authReadDataSource") readDataSource: DataSource,
    ): DataSource =
        LazyConnectionDataSourceProxy(writeDataSource).apply {
            setReadOnlyDataSource(readDataSource)
        }
}