package com.bookticket.booking_service.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableRetry
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Custom executor for booking event processing (Kafka/REST notifications)
     * Dedicated thread pool ensures event sending doesn't block main booking flow
     */
    @Bean(name = "bookingEventExecutor")
    public Executor bookingEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // minimum number of threads to keep alive
        executor.setMaxPoolSize(10); // maximum number of threads to spawn
        executor.setQueueCapacity(100); // number of tasks to queue before rejecting
        executor.setThreadNamePrefix("booking-event-");
        
        // Rejection policy - caller runs the task if queue is full
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.warn("Booking event executor queue is full. Task will be executed in caller thread.");
            r.run();
        });
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        log.info("Initialized booking event executor with core pool size: {}, max pool size: {}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize());
        
        return executor;
    }

    /**
     * Default executor for other async operations
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}

