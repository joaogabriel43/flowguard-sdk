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
     * Toggles the enabled field of a flag. Synchronized on the same monitor as replace()
     * to prevent a toggle event from being silently discarded during a concurrent snapshot swap.
     */
    public synchronized void toggle(String key) {
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
                flag.rolloutPercentage(),
                flag.rules()
        ));
    }

    public int size() {
        return store.size();
    }
}
