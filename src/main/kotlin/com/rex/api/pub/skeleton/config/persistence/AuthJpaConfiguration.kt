package com.rex.api.pub.skeleton.config.persistence

import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jpa.EntityManagerFactoryBuilder
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.rex.api.pub.skeleton.persistence.jpa.repository.auth",
    ],
    entityManagerFactoryRef = "authEntityManagerFactory",
    transactionManagerRef = "authTransactionManager",
)
class AuthJpaConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    fun authEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("authDataSource") authDataSource: DataSource,
    ): LocalContainerEntityManagerFactoryBean =
        builder
            .dataSource(authDataSource)
            .packages("com.rex.api.pub.skeleton.persistence.jpa.entity.auth")
            .persistenceUnit("auth")
            .properties(mapOf("hibernate.hbm2ddl.auto" to "none"))
            .build()

    @Bean
    fun authTransactionManager(
        @Qualifier("authEntityManagerFactory") authEntityManagerFactory: EntityManagerFactory?,
    ): PlatformTransactionManager {
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = authEntityManagerFactory

        return transactionManager
    }
}