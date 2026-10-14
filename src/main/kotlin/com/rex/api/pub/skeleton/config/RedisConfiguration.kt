package com.rex.api.pub.skeleton.config

import io.lettuce.core.SslOptions
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.time.Duration

/**
 * 관리형 Redis 클러스터(단일 엔드포인트) 연결 설정 — `app.redis.managed-cluster=true` 일 때만 켜진다.
 *
 * Azure Managed Redis·ElastiCache(클러스터 모드)·Memorystore Cluster 처럼 클러스터 프로토콜을 쓰면서 접속
 * 엔드포인트가 하나인 서비스용이다. `spring.data.redis.cluster.nodes` 가 없으면 `host:port` 를 단일 노드로 쓴다.
 * 꺼져 있으면(기본) Boot 자동 구성이 `spring.data.redis.*` 로 standalone(또는 cluster.nodes) 연결을 만든다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.redis", name = ["managed-cluster"], havingValue = "true")
class RedisConfiguration(
    private val redisProperties: DataRedisProperties,
) {
    @Bean
    fun lettuceConnectionFactory(): LettuceConnectionFactory {
        val nodes =
            redisProperties.cluster?.nodes?.takeIf { it.isNotEmpty() }
                ?: listOf("${redisProperties.host}:${redisProperties.port}")
        val clusterConfig = RedisClusterConfiguration(nodes)
        clusterConfig.password = RedisPassword.of(redisProperties.password)

        val topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofSeconds(30))
            .enableAllAdaptiveRefreshTriggers()
            .build()

        val clusterClientOptions = ClusterClientOptions.builder()
            .topologyRefreshOptions(topologyRefreshOptions)
            .sslOptions(SslOptions.builder().jdkSslProvider().build())
            .validateClusterNodeMembership(false)
            .build()

        val clientConfig = LettuceClientConfiguration.builder()
            .clientOptions(clusterClientOptions)
            .apply {
                if (redisProperties.ssl.isEnabled) {
                    useSsl().disablePeerVerification()
                }
            }.build()

        return LettuceConnectionFactory(clusterConfig, clientConfig)
    }
}