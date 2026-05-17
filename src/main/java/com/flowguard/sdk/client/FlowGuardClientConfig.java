package com.flowguard.sdk.client;

public class FlowGuardClientConfig {

    private final String serverUrl;
    private final String apiKey;
    private final String tenantId;
    private final boolean defaultFallback;
    private final int connectionTimeoutSeconds;
    private final int readTimeoutSeconds;

    private FlowGuardClientConfig(Builder builder) {
        this.serverUrl = builder.serverUrl;
        this.apiKey = builder.apiKey;
        this.tenantId = builder.tenantId;
        this.defaultFallback = builder.defaultFallback;
        this.connectionTimeoutSeconds = builder.connectionTimeoutSeconds;
        this.readTimeoutSeconds = builder.readTimeoutSeconds;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public boolean isDefaultFallback() {
        return defaultFallback;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serverUrl = "http://localhost:8080";
        private String apiKey;
        private String tenantId;
        private boolean defaultFallback = false;
        private int connectionTimeoutSeconds = 5;
        private int readTimeoutSeconds = 5;

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder defaultFallback(boolean defaultFallback) {
            this.defaultFallback = defaultFallback;
            return this;
        }

        public Builder connectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
            return this;
        }

        public Builder readTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
            return this;
        }

        public FlowGuardClientConfig build() {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("API Key is required");
            }
            if (tenantId == null || tenantId.trim().isEmpty()) {
                throw new IllegalArgumentException("Tenant ID is required");
            }
            return new FlowGuardClientConfig(this);
        }
    }
}
