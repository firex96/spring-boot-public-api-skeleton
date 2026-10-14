package com.rex.api.pub.skeleton.config.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * 테스트 환경용 Security Configuration.
 *
 * 통합 테스트에서 인증 없이 API를 테스트할 수 있도록 모든 요청을 허용합니다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class, OAuth2Properties::class, OidcProperties::class, SecurityProperties::class)
@Profile("test")
class TestSecurityConfiguration {
    @Bean
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            .build()
}