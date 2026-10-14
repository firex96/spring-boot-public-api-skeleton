package com.rex.api.pub.skeleton.config.persistence

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

@Configuration
class MainDataSourceConfiguration {
    @Primary
    @Bean("mainWriteDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.main.write")
    fun mainWriteDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean("mainReadDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.main.read")
    fun mainReadDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean("mainWriteDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.main.write.hikari")
    fun mainWriteDataSource(
        @Qualifier("mainWriteDataSourceProperties") properties: DataSourceProperties,
    ): DataSource = properties.initializeDataSourceBuilder().build()

    @Bean("mainReadDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.main.read.hikari")
    fun mainReadDataSource(
        @Qualifier("mainReadDataSourceProperties") properties: DataSourceProperties,
    ): DataSource = properties.initializeDataSourceBuilder().build()

    @Primary
    @Bean("mainDataSource")
    fun mainDataSource(
        @Qualifier("mainWriteDataSource") writeDataSource: DataSource,
        @Qualifier("mainReadDataSource") readDataSource: DataSource,
    ): DataSource =
        LazyConnectionDataSourceProxy(writeDataSource).apply {
            setReadOnlyDataSource(readDataSource)
        }
}