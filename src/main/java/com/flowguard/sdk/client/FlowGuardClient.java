package com.flowguard.sdk.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowguard.sdk.cache.FlagCache;
import com.flowguard.sdk.core.model.Flag;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FlowGuardClient {

    private static final Logger logger = LoggerFactory.getLogger(FlowGuardClient.class);

    private final FlowGuardClientConfig config;
    private final FlagCache cache;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FlowGuardClient(FlowGuardClientConfig config, FlagCache cache) {
        this.config = config;
        this.cache = cache;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Synchronously load feature flags from FlowGuard server into the cache.
     * Operates silently on network failure to ensure application startup is resilient.
     */
    public void loadFlags() {
        String url = config.getServerUrl();
        if (url.endsWith("/")) {
            url += "api/flags";
        } else {
            url += "/api/flags";
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("X-Tenant-ID", config.getTenantId())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to load feature flags from server. HTTP Status: {}. Operating in fallback mode.", response.code());
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                logger.warn("Received empty response body from feature flag server.");
                return;
            }

            String json = body.string();
            List<Flag> flags = objectMapper.readValue(json, new TypeReference<List<Flag>>() {});

            Map<String, Flag> flagMap = new HashMap<>();
            for (Flag flag : flags) {
                if (flag.key() != null) {
                    flagMap.put(flag.key(), flag);
                }
            }

            cache.replace(flagMap);
            logger.info("Successfully loaded {} feature flags into cache from FlowGuard server.", flagMap.size());

        } catch (IOException e) {
            logger.warn("Unable to connect to FlowGuard server at {}. Operating in fallback mode: {}", url, e.getMessage());
        } catch (Exception e) {
            logger.warn("Unexpected error occurred while loading feature flags. Operating in fallback mode: {}", e.getMessage(), e);
        }
    }

    /**
     * Synchronously fetches details of a single feature flag from the server.
     * Used by the SSE Listener to hydtrate the cache on enxutos/change updates.
     */
    public Flag fetchSingleFlag(String flagKey) {
        if (flagKey == null || flagKey.trim().isEmpty()) {
            return null;
        }

        String url = config.getServerUrl();
        if (url.endsWith("/")) {
            url += "api/flags/" + flagKey;
        } else {
            url += "/api/flags/" + flagKey;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("X-Tenant-ID", config.getTenantId())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to fetch details for single flag '{}'. HTTP Status: {}.", flagKey, response.code());
                return null;
            }

            ResponseBody body = response.body();
            if (body == null) {
                logger.warn("Received empty response body for single flag '{}'.", flagKey);
                return null;
            }

            String json = body.string();
            return objectMapper.readValue(json, Flag.class);

        } catch (IOException e) {
            logger.warn("Unable to connect to FlowGuard server at {} to fetch single flag: {}", url, e.getMessage());
        } catch (Exception e) {
            logger.warn("Unexpected error occurred while fetching single flag: {}", e.getMessage(), e);
        }

        return null;
    }
}
