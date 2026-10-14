package com.rex.api.pub.skeleton.config.persistence

import com.rex.api.pub.skeleton.persistence.jpa.entity.backbone.SimpleAuditorAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing 설정.
 *
 * `@CreatedDate`·`@LastModifiedDate`·`@CreatedBy`·`@LastModifiedBy` 를 활성화한다.
 *
 * **`auditorAwareRef` 를 명시한다.** 생략하면 `AuditorAware` 타입 빈이
 * 하나일 때만 해결되고, 빈이 하나 더 생기는 순간 기동이 모호성으로 실패한다.
 * 빈 이름은 구현 클래스가 아니라 **역할**로 짓는다 — 구현을 갈아끼워도
 * 이름이 거짓이 되지 않는다.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
class AuditConfiguration {
    @Bean
    fun auditorProvider(): AuditorAware<Long> = SimpleAuditorAware()
}