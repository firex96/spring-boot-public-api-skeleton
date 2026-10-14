package com.rex.api.pub.skeleton.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.support.CompositeTaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfiguration {
    @Bean
    fun taskExecutor(): TaskExecutor {
        val threadPoolTaskExecutor = ThreadPoolTaskExecutor()
        threadPoolTaskExecutor.corePoolSize = 10

        threadPoolTaskExecutor.setTaskDecorator(CompositeTaskDecorator(listOf(MdcTaskDecorator(), AuditorContextTaskDecorator())))
        return threadPoolTaskExecutor
    }
}