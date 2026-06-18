package com.coderag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置
 * 适配 FC 90 秒超时限制，所有耗时任务强制异步化
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Value("${async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${async.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:50}")
    private int queueCapacity;

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("coderag-async-");
        executor.setRejectedExecutionHandler((r, e) -> {
            throw new RuntimeException("异步任务队列已满，请稍后重试");
        });
        executor.initialize();
        return executor;
    }
}
