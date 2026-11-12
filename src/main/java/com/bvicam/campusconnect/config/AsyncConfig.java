package com.bvicam.campusconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures bounded executor beans so {@code @Async} background tasks (BVICAM portal sync,
 * email broadcasts, and document OCR/vectorization jobs) never spawn unbounded OS threads or
 * emit executor ambiguity warnings alongside WebSocket channel executors.
 */
@Configuration(proxyBeanMethods = false)
public class AsyncConfig {

    @Bean("applicationTaskExecutor")
    SimpleAsyncTaskExecutor applicationTaskExecutor() {
        return new SimpleAsyncTaskExecutor("app-");
    }

    @Bean("taskExecutor")
    ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("async-");
        return executor;
    }
}