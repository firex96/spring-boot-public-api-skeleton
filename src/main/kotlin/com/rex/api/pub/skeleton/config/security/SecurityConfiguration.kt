package com.rex.api.pub.skeleton.config.security

import com.rex.api.pub.skeleton.config.audit.AuditProperties
import com.rex.api.pub.skeleton.config.security.browser.BrowserSecurityPaths
import com.rex.api.pub.skeleton.config.security.browser.BrowserSessionValidationFilter
import com.rex.api.pub.skeleton.enums.ErrorCode
import com.rex.api.pub.skeleton.handler.ErrorReason
import com.rex.api.pub.skeleton.service.auth.AuthBizService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(JwtProperties::class, OAuth2Properties::class, OidcProperties::class, SecurityProperties::class)
@Profile("!test")
class SecurityConfiguration(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val securityErrorResponseWriter: SecurityErrorResponseWriter,
    private val customPermissionEvaluator: CustomPermissionEvaluator,
    private val auditProperties: AuditProperties,
    private val authBizService: AuthBizService,
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

    /**
     * `JwtAuthenticationFilter` 는 `@Component` 라 서블릿 필터로도 자동 등록된다 — 그러면 브라우저 체인 경로에서도
     * Bearer 가 SecurityContext 를 채운다. 자동 등록을 꺼서 JWT 인증은 API 체인 안에서만 일어나게 한다.
     */
    @Bean
    fun jwtAuthenticationFilterRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> = FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun methodSecurityExpressionHandler(): MethodSecurityExpressionHandler =
        DefaultMethodSecurityExpressionHandler().apply {
            setPermissionEvaluator(customPermissionEvaluator)
        }

    /**
     * 브라우저 체인 — 사용자 에이전트가 리다이렉트로 오가는 흐름(경로 정본: [BrowserSecurityPaths]).
     *
     * 세션(`SKELETON_SESSION`, Redis)과 CSRF 를 쓰고 JWT 는 읽지 않는다. 세션 속성은 SecurityContext·CSRF 토큰·
     * 대기 중인 authorize URL·2단계 challenge 토큰(문자열)뿐이다 — 요청 캐시를 끄는 이유다. 1회 소비 값(state·토큰 교환)은 세션이 아니라 Redis `GETDEL` 로 다룬다.
     * CORS 는 끈다: 쿠키를 싣는 경로를 교차 출처에 열지 않는다.
     */
    @Bean
    @Order(1)
    fun browserSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(*BrowserSecurityPaths.PATHS)
            .cors { it.disable() }
            .csrf { it.csrfTokenRepository(HttpSessionCsrfTokenRepository()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .securityContext { it.securityContextRepository(HttpSessionSecurityContextRepository()) }
            .requestCache { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        BrowserSecurityPaths.SOCIAL_AUTHORIZE,
                        BrowserSecurityPaths.SOCIAL_CALLBACK,
                        BrowserSecurityPaths.DEMO_CALLBACK,
                        BrowserSecurityPaths.OAUTH2_AUTHORIZE,
                        BrowserSecurityPaths.OAUTH2_LOGIN,
                        // 2단계 화면 — 진행 근거는 세션의 challenge 토큰과 Redis challenge 뿐이다(로그인 전 상태)
                        BrowserSecurityPaths.AUTH_MFA,
                    ).permitAll()
                    .requestMatchers(BrowserSecurityPaths.OAUTH2_CONSENT, BrowserSecurityPaths.SESSION_API).authenticated()
                    .anyRequest().denyAll()
            }
            .exceptionHandling { ex ->
                ex
                    // JSON API(토큰 교환)는 401 본문, 화면 경로(동의)는 로그인 화면으로
                    .defaultAuthenticationEntryPointFor(
                        customAuthenticationEntryPoint(),
                        PathPatternRequestMatcher.withDefaults().matcher(BrowserSecurityPaths.SESSION_API),
                    ).defaultAuthenticationEntryPointFor(LoginUrlAuthenticationEntryPoint(BrowserSecurityPaths.OAUTH2_LOGIN), AnyRequestMatcher.INSTANCE)
                    .accessDeniedHandler(customAccessDeniedHandler())
            }
            // 세션이 가리키는 사용자 세션이 폐기됐으면(로그아웃·강제 종료) 브라우저 세션도 버린다
            .addFilterAfter(BrowserSessionValidationFilter(authBizService), SecurityContextHolderFilter::class.java)
            .addFilterAfter(securityAuditorContextFilter(), BrowserSessionValidationFilter::class.java)
            .build()

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 API
                    .requestMatchers("/auth/login", "/auth/login/mfa", "/auth/signup", "/auth/refresh").permitAll()
                    // 소셜 로그인 코드 교환 - 공개 (인가 시작·콜백은 브라우저 체인)
                    .requestMatchers("/auth/oauth2/*/login").permitAll()
                    // 소셜 계정 연동/해제/조회 - 인증 필요
                    .requestMatchers("/auth/oauth2/*/link", "/auth/oauth2/*/unlink", "/auth/oauth2/accounts").authenticated()
                    // 비밀번호 재설정 (비인증 접근)
                    .requestMatchers("/user/password/reset-request", "/user/password/reset").permitAll()
                    // 이메일 인증 (비인증 접근)
                    .requestMatchers("/user/email/verify").permitAll()
                    // OAuth2 Authorization Server 엔드포인트 (authorize·login·consent 는 브라우저 체인)
                    .requestMatchers("/oauth2/token", "/oauth2/introspect", "/oauth2/revoke").permitAll()
                    // OpenID Connect 엔드포인트
                    // userinfo 는 인가 서버 access 토큰을 컨트롤러가 직접 검증한다(RFC 6750 오류) — JwtAuthenticationFilter 는 이 경로를 건너뛴다
                    .requestMatchers("/.well-known/openid-configuration", "/.well-known/jwks.json", "/oauth2/userinfo").permitAll()
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
        AuthenticationEntryPoint { _, response, _ ->
            securityErrorResponseWriter.write(response, ErrorCode.UNAUTHORIZED_ERROR, ErrorReason.UNAUTHORIZED)
        }

    private fun customAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { _, response, _ ->
            securityErrorResponseWriter.write(response, ErrorCode.FORBIDDEN_ERROR, ErrorReason.FORBIDDEN)
        }
}