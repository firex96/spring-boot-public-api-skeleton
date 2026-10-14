package com.rex.api.pub.skeleton.config

import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HttpExchangeConfiguration {
    @Bean
    fun httpExchangeRepository(): InMemoryHttpExchangeRepository = InMemoryHttpExchangeRepository()
}