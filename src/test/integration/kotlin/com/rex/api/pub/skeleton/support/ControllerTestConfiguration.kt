package com.rex.api.pub.skeleton.support

import com.rex.api.pub.skeleton.exception.ErrorMessageService
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import com.rex.api.pub.skeleton.service.auth.JwtTokenBizService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 컨트롤러 테스트용 공통 설정.
 *
 * @WebMvcTest에서 필요한 의존성 Bean들을 Mock으로 제공합니다.
 * JwtAuthenticationFilter, GlobalExceptionHandler 등이 요구하는 Bean들을 정의합니다.
 */
@TestConfiguration
class ControllerTestConfiguration {
    @Bean
    @Primary
    fun jwtTokenBizService(): JwtTokenBizService = mockk(relaxed = true)

    @Bean
    @Primary
    fun authServiceForFilter(): AuthBizService = mockk(relaxed = true)

    @Bean
    @Primary
    fun errorMessageService(): ErrorMessageService = mockk(relaxed = true)

    /**
     * `@AuthenticationPrincipal` 해석기 — `@WebMvcTest(addFilters = false)` 에서는 Spring Security 의 MVC 통합이 빠져
     * `AuthenticatedUser` 파라미터가 `@ModelAttribute` 로 바인딩돼 400 이 난다. 해석기를 직접 등록하면
     * `with(authentication(...))` 로 넣은 principal 이 그대로 주입되고, 인증 없는 요청은 nullable 파라미터에 null 이 들어간다.
     */
    @Bean
    fun authenticationPrincipalResolverConfigurer(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(AuthenticationPrincipalArgumentResolver())
            }
        }
}