package com.flowguard.sdk.cache;

public class FallbackStrategy {

    private final boolean defaultFallback;

    public FallbackStrategy(boolean defaultFallback) {
        this.defaultFallback = defaultFallback;
    }

    public boolean evaluate() {
        return defaultFallback;
    }
}
