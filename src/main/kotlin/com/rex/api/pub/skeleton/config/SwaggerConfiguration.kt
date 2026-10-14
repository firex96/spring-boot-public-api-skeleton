package com.rex.api.pub.skeleton.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger/OpenAPI 설정.
 *
 * API 그룹은 번호로 정렬됩니다:
 * - 0. All: 전체 API
 * - 1~98: 도메인별 API 그룹
 * - 99. Actuator: 모니터링 API
 *
 * 새 도메인 추가 시 아래 예시를 참고하세요:
 * ```kotlin
 * @Bean
 * fun getDomainApi(): GroupedOpenApi =
 *     GroupedOpenApi.builder()
 *         .group("2. Domain")
 *         .packagesToScan("com.rex.api.pub.skeleton.controller.domain")
 *         .build()
 * ```
 */
@Configuration
class SwaggerConfiguration {
    companion object {
        private const val BASE_PACKAGE = "com.rex.api.pub.skeleton.controller"
    }

    @Bean
    fun getAllApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("0. All")
            .packagesToScan(BASE_PACKAGE)
            .build()

    @Bean
    fun getProductApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("1. Product")
            .pathsToMatch("/products/**")
            .build()

    // 도메인 추가 예시:
    // @Bean
    // fun getOrderApi(): GroupedOpenApi =
    //     GroupedOpenApi.builder()
    //         .group("2. Order")
    //         .packagesToScan("$BASE_PACKAGE.order")
    //         .build()

    @Bean
    fun getActuatorApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("99. Actuator")
            .pathsToMatch("/actuator/**")
            .build()

    @Bean
    fun getOpenApi(): OpenAPI = OpenAPI().components(Components()).info(getInfo())

    private fun getInfo(): Info =
        Info()
            .version("1.0.0")
            .description("Public API Skeleton REST API")
            .title("Public API Skeleton")
}