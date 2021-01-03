package com.r307.arbitrader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for the task executor.
 */
@Configuration
public class ExecutorConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() + 1);
//        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-trade-pool-");
        executor.initialize();

        return executor;
    }
}
