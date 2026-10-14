package com.rex.api.pub.skeleton

import com.rex.api.pub.skeleton.config.audit.AuditProperties
import com.rex.api.pub.skeleton.config.client.ClientDestinations
import com.rex.api.pub.skeleton.config.client.ClientProperties
import com.rex.api.pub.skeleton.config.notification.MailProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    ClientDestinations::class,
    ClientProperties::class,
    // 자동 스캔이 아니라 명시 목록이다 — 빠뜨리면 빈이 생성되지 않아
    // TraceIdFilter 주입이 실패하고 컨텍스트 로딩부터 깨진다.
    AuditProperties::class,
    MailProperties::class,
)
class PublicApiSkeletonApplication

fun main(args: Array<String>) {
    runApplication<PublicApiSkeletonApplication>(*args)
}