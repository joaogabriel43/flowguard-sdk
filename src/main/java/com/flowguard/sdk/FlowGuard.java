package com.flowguard.sdk;

import com.flowguard.sdk.cache.FallbackStrategy;
import com.flowguard.sdk.cache.FlagCache;
import com.flowguard.sdk.client.FlowGuardClient;
import com.flowguard.sdk.client.FlowGuardClientConfig;
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

    public FlowGuard(FlowGuardClientConfig config) {
        this.cache = new FlagCache();
        this.evaluator = new LocalEvaluator();
        this.fallbackStrategy = new FallbackStrategy(config.isDefaultFallback());
        this.client = new FlowGuardClient(config, this.cache);
        
        // Initial flag fetch at startup
        refreshCache();
    }

    /**
     * Helper constructor for testing with mock components.
     */
    protected FlowGuard(FlagCache cache, LocalEvaluator evaluator, FallbackStrategy fallbackStrategy, FlowGuardClient client) {
        this.cache = cache;
        this.evaluator = evaluator;
        this.fallbackStrategy = fallbackStrategy;
        this.client = client;
    }

    /**
     * Core public API to evaluate feature flags locally.
     */
    public boolean isEnabled(String flagKey, String userId) {
        if (flagKey == null || flagKey.trim().isEmpty()) {
            logger.debug("Flag key is null or empty, falling back.");
            return fallbackStrategy.evaluate();
        }

        Flag flag = cache.get(flagKey);
        if (flag == null) {
            logger.debug("Flag '{}' not found in cache. Falling back.", flagKey);
            return fallbackStrategy.evaluate();
        }

        EvaluationResult result = evaluator.evaluate(flag, userId);
        logger.debug("Evaluated flag '{}' for user '{}'. Result: {}, Reason: {}", 
                flagKey, userId, result.enabled(), result.reason());
        
        return result.enabled();
    }

    /**
     * Core public API that accepts attributes for segment evaluation (YAGNI in Sprint 01).
     */
    public boolean isEnabled(String flagKey, String userId, Map<String, String> attributes) {
        // Segmentation YAGNI: Delegate directly to key+userId evaluation
        return isEnabled(flagKey, userId);
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
