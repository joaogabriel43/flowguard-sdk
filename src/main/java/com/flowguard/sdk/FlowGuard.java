package com.flowguard.sdk;

import com.flowguard.sdk.cache.FallbackStrategy;
import com.flowguard.sdk.cache.FlagCache;
import com.flowguard.sdk.client.FlowGuardClient;
import com.flowguard.sdk.client.FlowGuardClientConfig;
import com.flowguard.sdk.client.FlagSseListener;
import com.flowguard.sdk.core.evaluator.LocalEvaluator;
import com.flowguard.sdk.core.model.EvaluationResult;
import com.flowguard.sdk.core.model.Flag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FlowGuard {

    private static final Logger logger = LoggerFactory.getLogger(FlowGuard.class);

    private final FlagCache cache;
    private final LocalEvaluator evaluator;
    private final FallbackStrategy fallbackStrategy;
    private final FlowGuardClient client;
    private final FlagSseListener sseListener;

    public FlowGuard(FlowGuardClientConfig config) {
        this.cache = new FlagCache();
        this.evaluator = new LocalEvaluator();
        this.fallbackStrategy = new FallbackStrategy(config.isDefaultFallback());
        this.client = new FlowGuardClient(config, this.cache);
        this.sseListener = new FlagSseListener(config, this.cache, this.client);
    }

    /**
     * Helper constructor for testing with mock components.
     */
    public FlowGuard(FlagCache cache, LocalEvaluator evaluator, FallbackStrategy fallbackStrategy, FlowGuardClient client, FlagSseListener sseListener) {
        this.cache = cache;
        this.evaluator = evaluator;
        this.fallbackStrategy = fallbackStrategy;
        this.client = client;
        this.sseListener = sseListener;
    }

    /**
     * Legacy constructor overload for backward compatibility with Sprint 01 tests.
     */
    public FlowGuard(FlagCache cache, LocalEvaluator evaluator, FallbackStrategy fallbackStrategy, FlowGuardClient client) {
        this(cache, evaluator, fallbackStrategy, client, null);
    }

    /**
     * Initializes SDK connection: executes initial cache hydration and spawns the SSE listener.
     */
    public synchronized void connect() {
        logger.info("Connecting FlowGuard SDK facade...");
        refreshCache();
        if (sseListener == null) {
            logger.warn("SSE Listener not configured; real-time updates are disabled.");
            return;
        }
        sseListener.start();
    }

    /**
     * Closes the SSE connection and releases background thread pools gracefully.
     */
    public synchronized void disconnect() {
        logger.info("Disconnecting FlowGuard SDK facade...");
        if (sseListener == null) {
            logger.warn("SSE Listener not configured; nothing to disconnect.");
            return;
        }
        sseListener.stop();
    }

    /**
     * Core public API to evaluate feature flags locally.
     */
    public boolean isEnabled(String flagKey, String userId) {
        return isEnabled(flagKey, userId, Map.of());
    }

    /**
     * Core public API that accepts attributes for segment evaluation.
     */
    public boolean isEnabled(String flagKey, String userId, Map<String, String> attributes) {
        if (flagKey == null || flagKey.trim().isEmpty()) {
            logger.warn("Flag key is null or empty, falling back.");
            return fallbackStrategy.evaluate();
        }

        if (userId == null) {
            logger.warn("User ID is null for flag '{}', falling back.", flagKey);
            return fallbackStrategy.evaluate();
        }

        Flag flag = cache.get(flagKey);
        if (flag == null) {
            logger.debug("Flag '{}' not found in cache. Falling back.", flagKey);
            return fallbackStrategy.evaluate();
        }

        EvaluationResult result = evaluator.evaluate(flag, userId, attributes);
        logger.debug("Evaluated flag '{}' for user '{}' with attributes. Result: {}, Reason: {}", 
                flagKey, userId, result.enabled(), result.reason());
        
        return result.enabled();
    }

    /**
     * Manually triggers cache synchronization.
     */
    public void refreshCache() {
        logger.debug("Triggering manual refresh of FlowGuard feature flag cache...");
        client.loadFlags();
    }

    /**
     * Access to cache for internal/test validations.
     */
    protected FlagCache getCache() {
        return cache;
    }
}
