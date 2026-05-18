package com.flowguard.sdk.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowguard.sdk.cache.FlagCache;
import com.flowguard.sdk.core.model.Flag;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FlagSseListener extends EventSourceListener {

    private static final Logger logger = LoggerFactory.getLogger(FlagSseListener.class);

    private final FlowGuardClientConfig config;
    private final FlagCache cache;
    private final FlowGuardClient client;
    private final ObjectMapper objectMapper;

    private OkHttpClient sseHttpClient;
    private EventSource.Factory eventSourceFactory;
    private ExecutorService executorService;

    // Dedicated executor for HTTP calls triggered by SSE events; prevents blocking the OkHttp callback thread.
    private ExecutorService cacheUpdateExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private EventSource currentEventSource;

    // A-02: volatile ensures cross-thread visibility; AtomicInteger guarantees atomic read-modify-write.
    private final AtomicInteger attempt = new AtomicInteger(0);

    public FlagSseListener(FlowGuardClientConfig config, FlagCache cache, FlowGuardClient client) {
        this.config = config;
        this.cache = cache;
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Starts the SSE listener background connection loop.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting FlowGuard SSE Listener...");
            attempt.set(0);

            // SSE-dedicated OkHttpClient with infinite read timeout to allow long-lived streams
            this.sseHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build();

            this.eventSourceFactory = EventSources.createFactory(sseHttpClient);

            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "flowguard-sse-worker");
                thread.setDaemon(true);
                return thread;
            });

            // M-02: separate single-threaded executor so HTTP fetches never block the OkHttp callback thread.
            this.cacheUpdateExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "flowguard-cache-updater");
                thread.setDaemon(true);
                return thread;
            });

            executorService.submit(this::connectLoop);
        }
    }

    /**
     * Shuts down the connection and releases background thread pools gracefully.
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping FlowGuard SSE Listener...");

            // Cancel current SSE subscription if active
            if (currentEventSource != null) {
                currentEventSource.cancel();
                currentEventSource = null;
            }

            if (executorService != null) {
                executorService.shutdownNow();
                try {
                    if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                        logger.warn("SSE connection thread pool did not terminate gracefully.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executorService = null;
            }

            if (cacheUpdateExecutor != null) {
                cacheUpdateExecutor.shutdownNow();
                try {
                    if (!cacheUpdateExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                        logger.warn("Cache update thread pool did not terminate gracefully.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cacheUpdateExecutor = null;
            }

            if (sseHttpClient != null) {
                sseHttpClient.dispatcher().executorService().shutdownNow();
                sseHttpClient.connectionPool().evictAll();
                sseHttpClient = null;
            }

            // Notify wait locks to exit
            this.notifyAll();
        }
    }

    /**
     * Loop executing on a background thread managing retries and backoff.
     */
    private void connectLoop() {
        while (running.get()) {
            try {
                connectSse();

                // Keep background thread blocked until connection failure occurs or listener stopped
                synchronized (this) {
                    while (running.get() && currentEventSource != null) {
                        this.wait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Unexpected error initiating SSE stream: {}", e.getMessage());
            }

            if (running.get()) {
                long delaySeconds = calculateBackoff(attempt.getAndIncrement());
                logger.warn("FlowGuard SSE disconnected. Retrying connection in {}s (attempt {})...", delaySeconds, attempt.get());
                try {
                    TimeUnit.SECONDS.sleep(delaySeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Calculates the exponential backoff delay capped at 30 seconds.
     */
    public static long calculateBackoff(int attempt) {
        if (attempt < 0) {
            return 1;
        }
        long backoff = (long) Math.pow(2, attempt);
        return Math.min(30, backoff);
    }

    /**
     * Instantiates the EventSource subscription.
     */
    private synchronized void connectSse() {
        if (!running.get()) {
            return;
        }

        String url = config.getServerUrl();
        if (url.endsWith("/")) {
            url += "api/sse/flags";
        } else {
            url += "/api/sse/flags";
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("X-Tenant-ID", config.getTenantId())
                .build();

        logger.debug("Establishing new OkHttp EventSource against URL: {}", url);
        currentEventSource = eventSourceFactory.newEventSource(request, this);
    }

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        logger.info("Successfully established FlowGuard SSE real-time streaming channel.");
        synchronized (this) {
            attempt.set(0); // Reset backoff attempts on successful handshake
        }
    }

    @Override
    public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
        logger.debug("Received SSE Event. Type: {}, Data: {}", type, data);
        if (type == null || data == null || data.trim().isEmpty()) {
            return;
        }

        try {
            switch (type) {
                case "flag-snapshot":
                    processSnapshot(data);
                    break;
                case "flag-updated":
                    processUpdated(data);
                    break;
                case "flag-deleted":
                    processDeleted(data);
                    break;
                case "flag-toggled":
                    processToggled(data);
                    break;
                default:
                    logger.debug("Ignored unhandled SSE event type: {}", type);
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to process SSE Event of type '{}': {}", type, e.getMessage(), e);
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        logger.info("FlowGuard SSE streaming channel closed by server.");
        handleDisconnect();
    }

    @Override
    public void onFailure(EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        if (!running.get()) {
            return; // Silently ignore failures during graceful stop shutdowns
        }

        if (t != null) {
            logger.warn("FlowGuard SSE communication failure: {}", t.getMessage());
        } else if (response != null) {
            logger.warn("FlowGuard SSE endpoint returned HTTP error code: {}", response.code());
        } else {
            logger.warn("FlowGuard SSE unknown streaming channel failure.");
        }
        handleDisconnect();
    }

    private synchronized void handleDisconnect() {
        currentEventSource = null;
        this.notifyAll(); // Wake up connection loop thread to schedule backoff retry
    }

    /**
     * Atomically swaps the entire local cache with the received snapshot array.
     */
    private void processSnapshot(String data) throws Exception {
        List<Flag> flags = objectMapper.readValue(data, new TypeReference<List<Flag>>() {});
        Map<String, Flag> flagMap = new HashMap<>();
        for (Flag flag : flags) {
            if (flag.key() != null) {
                flagMap.put(flag.key(), flag);
            }
        }
        cache.replace(flagMap);
        logger.info("SSE: Synchronized local cache atomically with snapshot of {} flags.", flagMap.size());
    }

    /**
     * Parses the payload to insert or update a specific flag in the cache.
     * M-02: HTTP fetches are offloaded to cacheUpdateExecutor to avoid blocking the OkHttp callback thread.
     */
    private void processUpdated(String data) throws Exception {
        try {
            Map<String, Object> map = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
            if (map.containsKey("flagKey")) {
                // Slim event — fetch full flag from server off the callback thread
                String flagKey = (String) map.get("flagKey");
                logger.debug("SSE: flag-updated slim event for '{}'. Submitting hydration to cache updater.", flagKey);
                submitCacheUpdate(() -> {
                    Flag flag = client.fetchSingleFlag(flagKey);
                    if (flag != null) {
                        cache.put(flag.key(), flag);
                        logger.info("SSE: hydrated and updated flag '{}' in cache.", flag.key());
                    } else {
                        logger.warn("SSE: failed to hydrate flag '{}'. Reloading full cache.", flagKey);
                        client.loadFlags();
                    }
                });
            } else if (map.containsKey("key")) {
                // Full Flag payload — no HTTP needed, apply directly on the callback thread
                Flag flag = objectMapper.readValue(data, Flag.class);
                cache.put(flag.key(), flag);
                logger.info("SSE: inserted/updated flag '{}' in cache (direct payload).", flag.key());
            }
        } catch (Exception e) {
            logger.warn("SSE: error parsing flag-updated payload: {}. Submitting full recovery sync.", e.getMessage());
            submitCacheUpdate(client::loadFlags);
        }
    }

    /**
     * Parses the payload to remove the flag key from local cache.
     */
    private void processDeleted(String data) throws Exception {
        try {
            if (data.startsWith("{")) {
                Map<String, Object> map = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
                String key = (String) map.getOrDefault("flagKey", map.get("key"));
                if (key != null) {
                    cache.remove(key);
                    logger.info("SSE: Successfully removed deleted flag '{}' from cache.", key);
                }
            } else {
                // Raw key name
                cache.remove(data);
                logger.info("SSE: Successfully removed deleted flag '{}' from cache (raw key).", data);
            }
        } catch (Exception e) {
            logger.warn("SSE: Error processing flag-deleted payload: {}. Invoking full recovery sync.", e.getMessage());
            client.loadFlags();
        }
    }

    /**
     * Parses the payload to toggle the enabled state in-place.
     */
    private void processToggled(String data) throws Exception {
        try {
            if (data.startsWith("{")) {
                Map<String, Object> map = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
                String key = (String) map.getOrDefault("flagKey", map.get("key"));
                if (key != null) {
                    cache.toggle(key);
                    logger.info("SSE: Successfully toggled flag '{}' in cache.", key);
                }
            } else {
                // Raw key name
                cache.toggle(data);
                logger.info("SSE: Successfully toggled flag '{}' in cache (raw key).", data);
            }
        } catch (Exception e) {
            logger.warn("SSE: Error processing flag-toggled payload: {}. Invoking full recovery sync.", e.getMessage());
            client.loadFlags();
        }
    }

    /**
     * Submits a blocking cache-update task to the dedicated executor.
     * If the executor is unavailable (e.g. listener not started), executes inline as fallback.
     */
    private void submitCacheUpdate(CacheUpdateTask task) {
        ExecutorService executor = cacheUpdateExecutor;
        if (executor != null && !executor.isShutdown()) {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logger.error("SSE: cache update task failed: {}", e.getMessage(), e);
                }
            });
        } else {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("SSE: cache update task failed (inline): {}", e.getMessage(), e);
            }
        }
    }

    @FunctionalInterface
    private interface CacheUpdateTask {
        void run() throws Exception;
    }

    // Exposed for testing
    protected EventSource getCurrentEventSource() {
        return currentEventSource;
    }
}
