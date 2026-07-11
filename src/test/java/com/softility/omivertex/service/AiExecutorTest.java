package com.softility.omivertex.service;

import com.softility.omivertex.web.error.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExecutorTest {

    private final AiExecutor executor = new AiExecutor();

    @AfterEach
    void shutDown() {
        executor.shutdown();
    }

    @Test
    void submit_runsTheTaskOffTheCallerThread() throws Exception {
        String callerThread = Thread.currentThread().getName();
        CompletableFuture<String> result = executor.submit(() -> Thread.currentThread().getName());
        assertThat(result.get()).isNotEqualTo(callerThread).startsWith("ai-");
    }

    @Test
    void submit_whenPoolAndQueueAreFull_throwsServiceUnavailable() {
        CountDownLatch release = new CountDownLatch(1);
        try {
            // fill all threads and the whole queue with blocked tasks
            for (int i = 0; i < AiExecutor.POOL_SIZE + AiExecutor.QUEUE_CAPACITY; i++) {
                executor.submit(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            }
            assertThatThrownBy(() -> executor.submit(() -> "overflow"))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("busy");
        } finally {
            release.countDown();
        }
    }
}
