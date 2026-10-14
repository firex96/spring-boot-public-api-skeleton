package com.rex.api.pub.skeleton.config.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.client.properties")
class ClientProperties(
    val httpClient: HttpClientProperties,
    val webClient: WebClientProperties,
    val connection: ConnectionPoolProperties,
) {
    class HttpClientProperties(
        val connectionTimeout: Int,
    )

    class WebClientProperties(
        val memorySize: Int,
    )

    class ConnectionPoolProperties(
        val maxConnections: Int,
        val maxIdleTime: Int,
        val maxLifeTime: Int,
        val pendingAcquireTimeout: Int,
        val pendingAcquireMaxCount: Int,
        val evictInBackground: Int,
    )
}