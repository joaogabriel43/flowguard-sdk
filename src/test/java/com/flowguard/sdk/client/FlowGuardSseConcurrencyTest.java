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

class FlowGuardSseConcurrencyTest {

    @RepeatedTest(5)
    void shouldMaintainThreadSafetyDuringMassiveConcurrentReadsAndWrites() throws InterruptedException {
        // Given
        FlagCache cache = new FlagCache();
        LocalEvaluator evaluator = new LocalEvaluator();
        FallbackStrategy fallbackStrategy = new FallbackStrategy(false);
        FlowGuardClient client = null; // not needed for evaluations
        
        // Mock SSE Listener to bypass network calls
        FlagSseListener sseListener = null;

        FlowGuard flowGuard = new FlowGuard(cache, evaluator, fallbackStrategy, client, sseListener);

        UUID tenantId = UUID.randomUUID();
        String flagKey = "concurrency-flag";
        
        // Populate initial flag
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
        CountDownLatch finishLatch = new CountDownLatch(readerCount + 1);
        
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // 1. Writer Thread: Simulates SseListener processing SSE updates in background
        executorService.submit(() -> {
            try {
                startLatch.await();
                int iteration = 0;
                while (running.get()) {
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
                    
                    // Minor yield to prevent complete thread starvation of readers
                    Thread.yield();
                }
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
            } finally {
                finishLatch.countDown();
            }
        });

        // 2. Reader Threads: Simulate application threads calling isEnabled() concurrently
        for (int i = 0; i < readerCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerReader; j++) {
                        // isEnabled evaluates rollout percentages & maps, heavily accessing FlagCache
                        flowGuard.isEnabled(flagKey, "user-" + j);
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // When: Trigger concurrent operations
        startLatch.countDown();
        
        // Wait for readers to finish their operations
        // Let them run for up to 5 seconds max
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        running.set(false); // Stop writer if not stopped

        executorService.shutdownNow();

        // Then: Assert no concurrent modification or race exceptions were raised
        assertEquals(0, exceptionCount.get(), "No exceptions should be thrown during concurrent cache read/write operations");
    }
}
