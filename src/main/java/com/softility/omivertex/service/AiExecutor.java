package com.softility.omivertex.service;

import com.softility.omivertex.web.error.ServiceUnavailableException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Bulkhead for all Gemini work: AI calls run on this small dedicated pool so a
 * slow or hung upstream can never occupy servlet request threads, and a
 * saturated pool fails fast (503) instead of queueing unboundedly.
 */
@Component
public class AiExecutor {

    /** Concurrent Gemini calls; small on purpose — the upstream is the bottleneck. */
    static final int POOL_SIZE = 4;
    /** Requests allowed to wait for a thread before we start rejecting. */
    static final int QUEUE_CAPACITY = 8;

    private final ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();

    public AiExecutor() {
        pool.setCorePoolSize(POOL_SIZE);
        pool.setMaxPoolSize(POOL_SIZE);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix("ai-");
        pool.initialize();
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, pool);
        } catch (RejectedExecutionException e) {
            throw new ServiceUnavailableException(
                    "The AI assistant is busy right now — try again shortly");
        }
    }

    public void shutdown() {
        pool.shutdown();
    }
}
