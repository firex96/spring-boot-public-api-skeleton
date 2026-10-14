package com.rex.api.pub.skeleton.config.persistence

import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.rex.api.pub.skeleton.persistence.jpa.repository.main",
    ],
    entityManagerFactoryRef = "mainEntityManagerFactory",
    transactionManagerRef = "mainTransactionManager",
)
class MainJpaConfiguration {
    @Primary
    @Bean
    fun mainEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("mainDataSource") dataSource: DataSource,
    ): LocalContainerEntityManagerFactoryBean =
        builder
            .dataSource(dataSource)
            .packages("com.rex.api.pub.skeleton.persistence.jpa.entity.main")
            .persistenceUnit("main")
            .properties(mapOf("hibernate.hbm2ddl.auto" to "none"))
            .build()

    @Primary
    @Bean
    fun mainTransactionManager(
        @Qualifier("mainEntityManagerFactory") entityManagerFactory: EntityManagerFactory?,
    ): PlatformTransactionManager {
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = entityManagerFactory
        return transactionManager
    }
}