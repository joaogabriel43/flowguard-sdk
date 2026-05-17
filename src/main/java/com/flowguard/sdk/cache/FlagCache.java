package com.flowguard.sdk.cache;

import com.flowguard.sdk.core.model.Flag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FlagCache {

    private final Map<String, Flag> store = new ConcurrentHashMap<>();

    public Flag get(String key) {
        if (key == null) {
            return null;
        }
        return store.get(key);
    }

    public synchronized void put(String key, Flag flag) {
        if (key != null && flag != null) {
            store.put(key, flag);
        }
    }

    public synchronized void putAll(Map<String, Flag> flags) {
        if (flags != null) {
            store.putAll(flags);
        }
    }

    public synchronized void clear() {
        store.clear();
    }

    public synchronized void replace(Map<String, Flag> newFlags) {
        store.clear();
        if (newFlags != null) {
            store.putAll(newFlags);
        }
    }

    public synchronized void remove(String key) {
        if (key != null) {
            store.remove(key);
        }
    }

    /**
     * Atomically toggles the enabled field of a flag in the cache.
     * Uses ConcurrentHashMap's native atomic computeIfPresent for maximum performance.
     */
    public void toggle(String key) {
        if (key == null) {
            return;
        }
        store.computeIfPresent(key, (k, flag) -> new Flag(
                flag.id(),
                flag.tenantId(),
                flag.key(),
                flag.name(),
                flag.description(),
                !flag.enabled(),
                flag.rolloutPercentage()
        ));
    }

    public int size() {
        return store.size();
    }
}
