package com.example.todoapp2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean
    @Primary
    ThreadPoolTaskExecutor taskExecutor() {
        return executor("todo-task-", 2, 4, 16);
    }

    @Bean
    ThreadPoolTaskExecutor emailExecutor() {
        return executor("todo-email-", 1, 2, 16);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (failure, method, arguments) -> log.error("Async failure method={} exception={}",
                method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                failure.getClass().getSimpleName());
    }

    private ThreadPoolTaskExecutor executor(String prefix, int core, int max, int queue) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        return executor;
    }
}
