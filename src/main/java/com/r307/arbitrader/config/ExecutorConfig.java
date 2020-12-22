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
    public Executor tickerEventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Open to discussion
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("ticker-pool-");
        executor.initialize();

        return executor;
    }

    @Bean
    public Executor tradeAnalysisEventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Open to discussion
        executor.setCorePoolSize(15);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("analysis-pool-");
        executor.initialize();

        return executor;
    }
}
