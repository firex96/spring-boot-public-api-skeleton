package com.rex.api.pub.skeleton.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시간 소스 — 시간에 따라 결과가 갈리는 판정(TOTP 구간, 2단계 challenge 만료)이 주입받아 테스트에서 시각을 고정한다.
 * 기존 코드의 `now()` 직접 호출은 바꾸지 않는다.
 */
@Configuration
class ClockConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}