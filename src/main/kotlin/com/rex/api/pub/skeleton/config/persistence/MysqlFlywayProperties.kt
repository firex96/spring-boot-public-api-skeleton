package com.rex.api.pub.skeleton.config.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

/** 초기화가 활성화될 때만 바인딩되는 MySQL admin 자격증명. */
@ConfigurationProperties(prefix = "app.flyway.mysql")
class MysqlFlywayProperties(
    val username: String,
    val password: String,
)