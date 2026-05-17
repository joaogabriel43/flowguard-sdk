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

    public void put(String key, Flag flag) {
        if (key != null && flag != null) {
            store.put(key, flag);
        }
    }

    public void putAll(Map<String, Flag> flags) {
        if (flags != null) {
            store.putAll(flags);
        }
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
