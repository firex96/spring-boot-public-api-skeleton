package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.config.audit.AuditProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(JwtProperties::class, OAuth2Properties::class, OidcProperties::class, SecurityProperties::class)
@Profile("!test")
class SecurityConfiguration(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: JsonMapper,
    private val customPermissionEvaluator: CustomPermissionEvaluator,
    private val auditProperties: AuditProperties,
) {
    /**
     * 감사 주체를 채우는 필터.
     *
     * **`@Component` 를 붙이지 않는다** — Spring Boot 가 `Filter` 타입 빈을 서블릿
     * 필터로도 등록해 **인증 전에 한 번 더 돈다.** 그러면 헤더 폴백이 먼저 기록된다.
     * 아래 [securityAuditorContextFilterRegistration] 이 그 자동 등록을 끈다.
     */
    @Bean
    fun securityAuditorContextFilter(): SecurityAuditorContextFilter = SecurityAuditorContextFilter(auditProperties)

    /** 서블릿 자동 등록을 끈다 — 등록 경로는 Security 체인 하나뿐이어야 한다. */
    @Bean
    fun securityAuditorContextFilterRegistration(
        filter: SecurityAuditorContextFilter,
    ): FilterRegistrationBean<SecurityAuditorContextFilter> = FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun methodSecurityExpressionHandler(): MethodSecurityExpressionHandler =
        DefaultMethodSecurityExpressionHandler().apply {
            setPermissionEvaluator(customPermissionEvaluator)
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 API
                    .requestMatchers("/auth/login", "/auth/signup", "/auth/refresh").permitAll()
                    // 소셜 로그인 - 공개 (인증 URL, 로그인, 콜백)
                    .requestMatchers("/auth/oauth2/*/authorize", "/auth/oauth2/*/login", "/auth/oauth2/*/callback").permitAll()
                    // 소셜 계정 연동/해제/조회 - 인증 필요
                    .requestMatchers("/auth/oauth2/*/link", "/auth/oauth2/*/unlink", "/auth/oauth2/accounts").authenticated()
                    // 비밀번호 재설정 (비인증 접근)
                    .requestMatchers("/user/password/reset-request", "/user/password/reset").permitAll()
                    // 이메일 인증 (비인증 접근)
                    .requestMatchers("/user/email/verify").permitAll()
                    // OAuth2 Authorization Server 엔드포인트
                    .requestMatchers("/oauth2/authorize", "/oauth2/token", "/oauth2/introspect", "/oauth2/revoke").permitAll()
                    .requestMatchers("/oauth2/consent").authenticated()
                    // OpenID Connect 엔드포인트
                    .requestMatchers("/.well-known/openid-configuration", "/.well-known/jwks.json").permitAll()
                    .requestMatchers("/oauth2/userinfo").authenticated()
                    // Swagger UI
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    // Actuator (health check)
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    // 데모 페이지 및 정적 리소스
                    .requestMatchers("/demo/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                    // 관리자 API (SUPER_ADMIN 또는 ADMIN 그룹)
                    .requestMatchers("/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                    // 그 외 모든 요청은 인증 필요
                    .anyRequest().authenticated()
            }
            .exceptionHandling { ex ->
                ex
                    .authenticationEntryPoint(customAuthenticationEntryPoint())
                    .accessDeniedHandler(customAccessDeniedHandler())
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // SecurityContext 가 채워진 뒤에 감사 주체를 잡는다. 순서가 뒤바뀌면
            // 컨텍스트가 비어 헤더 폴백으로 떨어지고, 잘못된 주체가 조용히 기록된다.
            .addFilterAfter(securityAuditorContextFilter(), JwtAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOriginPatterns = listOf("*")
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600L
            }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    private fun customAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, authException ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE

            val body =
                mapOf(
                    "status" to HttpStatus.UNAUTHORIZED.value(),
                    "error" to "Unauthorized",
                    "message" to "인증이 필요합니다",
                    "path" to request.requestURI,
                )

            objectMapper.writeValue(response.outputStream, body)
        }

    private fun customAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { request, response, accessDeniedException ->
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE

            val body =
                mapOf(
                    "status" to HttpStatus.FORBIDDEN.value(),
                    "error" to "Forbidden",
                    "message" to "접근 권한이 없습니다",
                    "path" to request.requestURI,
                )

            objectMapper.writeValue(response.outputStream, body)
        }
}