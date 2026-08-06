package com.furkan.apidebugagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The pool analyses run on. Small on purpose: one analysis is a handful of HTTP calls to
 * {@code demo-api} plus at most one model call, and a debugging session runs a few of them.
 */
@Configuration
public class AnalysisExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("analysis-");
        // A running analysis is worth the few seconds it takes to finish on shutdown.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

}
