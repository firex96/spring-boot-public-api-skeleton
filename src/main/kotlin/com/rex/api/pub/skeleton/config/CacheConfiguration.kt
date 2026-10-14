package com.rex.api.pub.skeleton.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfiguration {
    @Value("\${spring.cache.redis.time-to-live}")
    private lateinit var timeToLive: Duration

    @Bean
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
    ): RedisCacheManager {
        val redisSerializer: RedisSerializer<Any> = RedisCacheSerializerFactory.create()

        val cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))
            // .disableCachingNullValues()
            .entryTtl(timeToLive)

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfiguration)
            .build()
    }
}