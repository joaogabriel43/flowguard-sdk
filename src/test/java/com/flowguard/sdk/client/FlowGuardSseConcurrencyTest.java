package com.flowguard.sdk.client;

import com.flowguard.sdk.FlowGuard;
import com.flowguard.sdk.cache.FallbackStrategy;
import com.flowguard.sdk.cache.FlagCache;
import com.flowguard.sdk.core.evaluator.LocalEvaluator;
import com.flowguard.sdk.core.model.Flag;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGuardSseConcurrencyTest {

    @RepeatedTest(5)
    void shouldMaintainThreadSafetyDuringMassiveConcurrentReadsAndWrites() throws InterruptedException {
        // Given
        FlagCache cache = new FlagCache();
        LocalEvaluator evaluator = new LocalEvaluator();
        FallbackStrategy fallbackStrategy = new FallbackStrategy(false);

        FlowGuard flowGuard = new FlowGuard(cache, evaluator, fallbackStrategy, null, null);

        UUID tenantId = UUID.randomUUID();
        String flagKey = "concurrency-flag";

        Flag flag = new Flag(
                UUID.randomUUID(),
                tenantId,
                flagKey,
                "Concurrency Flag",
                "Desc",
                true,
                50
        );
        cache.put(flagKey, flag);

        int readerCount = 20;
        int operationsPerReader = 1000;

        ExecutorService executorService = Executors.newFixedThreadPool(readerCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);

        // M-03 fix: latch only for readers — writer is stopped independently after readers finish.
        CountDownLatch readerFinishLatch = new CountDownLatch(readerCount);

        AtomicBoolean writerRunning = new AtomicBoolean(true);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // 1. Writer Thread: Simulates SseListener processing SSE updates in background
        executorService.submit(() -> {
            try {
                startLatch.await();
                int iteration = 0;
                while (writerRunning.get()) {
                    iteration++;

                    // Alternates between replace, put, toggle, and remove
                    if (iteration % 4 == 0) {
                        Map<String, Flag> newMap = new HashMap<>();
                        newMap.put(flagKey, new Flag(UUID.randomUUID(), tenantId, flagKey, "Replaced " + iteration, "Desc", iteration % 2 == 0, 50));
                        cache.replace(newMap);
                    } else if (iteration % 4 == 1) {
                        cache.put(flagKey, new Flag(UUID.randomUUID(), tenantId, flagKey, "Updated " + iteration, "Desc", iteration % 2 == 0, 50));
                    } else if (iteration % 4 == 2) {
                        cache.toggle(flagKey);
                    } else {
                        cache.remove(flagKey);
                    }

                    Thread.yield();
                }
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            }
        });

        // 2. Reader Threads: Simulate application threads calling isEnabled() concurrently
        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerReader; j++) {
                        flowGuard.isEnabled(flagKey, "user-" + j);
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    readerFinishLatch.countDown();
                }
            });
        }

        // When: Trigger concurrent operations
        startLatch.countDown();

        // M-03 fix: wait only for readers; writer is independent.
        boolean finished = readerFinishLatch.await(5, TimeUnit.SECONDS);

        // Stop writer after readers are done (or timed out)
        writerRunning.set(false);
        executorService.shutdownNow();

        // Then
        assertTrue(finished, "All reader threads must complete within the 5-second timeout");
        assertEquals(0, exceptionCount.get(), "No exceptions should be thrown during concurrent cache read/write operations");
    }
}
