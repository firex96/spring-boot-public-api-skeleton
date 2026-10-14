package com.rex.api.pub.skeleton.config

import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.format.DateTimeFormatter

@Configuration
class WebConfiguration : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/swagger-ui/").setViewName("forward:/swagger-ui/index.html")
    }

    override fun addFormatters(registry: FormatterRegistry) {
        // StringEnum ConverterFactory 등록
        registry.addConverterFactory(CodeConverterFactory())

        // ISO 날짜 포맷 등록
        val registrar = DateTimeFormatterRegistrar()
        registrar.setDateFormatter(DateTimeFormatter.ISO_DATE)
        registrar.setDateTimeFormatter(DateTimeFormatter.ISO_DATE_TIME)
        registrar.registerFormatters(registry)
    }
}